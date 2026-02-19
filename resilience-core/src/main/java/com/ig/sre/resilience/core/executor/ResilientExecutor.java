package com.ig.sre.resilience.core.executor;

import com.ig.sre.resilience.core.circuit.CircuitBreakerOpenException;
import com.ig.sre.resilience.core.circuit.CircuitState;
import com.ig.sre.resilience.core.clock.ResilienceClock;
import com.ig.sre.resilience.core.error.ErrorCategory;
import com.ig.sre.resilience.core.error.ErrorClassifier;
import com.ig.sre.resilience.core.error.UpstreamException;
import com.ig.sre.resilience.core.context.RequestContext;
import com.ig.sre.resilience.core.policy.PolicyProvider;
import com.ig.sre.resilience.core.policy.ResiliencePolicy;
import com.ig.sre.resilience.core.ratelimit.RateLimitDecision;
import com.ig.sre.resilience.core.ratelimit.RateLimitExceededException;
import com.ig.sre.resilience.core.ratelimit.RateLimiter;
import com.ig.sre.resilience.core.telemetry.Telemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public class ResilientExecutor implements AutoCloseable {

    private static final String REQUEST_OUTCOME_SUCCESS = "success";
    private static final String REQUEST_OUTCOME_UPSTREAM_ERROR = "upstream_error";
    private static final String REQUEST_OUTCOME_RATE_LIMITED = "rate_limited";
    private static final String REQUEST_OUTCOME_CLIENT_ERROR = "client_error";
    private static final String REQUEST_OUTCOME_UNKNOWN_ERROR = "unknown_error";

    private final PolicyProvider policyProvider;
    private final RateLimiter rateLimiter;
    private final Telemetry telemetry;
    private final ResilienceClock clock;
    private final ExecutorService timeoutExecutor;
    private final Map<String, CircuitBreakerRuntime> circuitBreakers = new ConcurrentHashMap<>();

    public ResilientExecutor(
            PolicyProvider policyProvider,
            RateLimiter rateLimiter,
            Telemetry telemetry,
            ResilienceClock clock
    ) {
        this(policyProvider, rateLimiter, telemetry, clock, Executors.newVirtualThreadPerTaskExecutor());
    }

    public ResilientExecutor(
            PolicyProvider policyProvider,
            RateLimiter rateLimiter,
            Telemetry telemetry,
            ResilienceClock clock,
            ExecutorService timeoutExecutor
    ) {
        this.policyProvider = Objects.requireNonNull(policyProvider, "policyProvider must not be null");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.timeoutExecutor = Objects.requireNonNull(timeoutExecutor, "timeoutExecutor must not be null");
    }

    public <T> T execute(Supplier<T> action, RequestContext context) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(context, "context must not be null");

        String dependencyKey = context.dependencyKey();
        String operationName = context.operationName();

        Telemetry.TelemetryContext telemetryContext = telemetry.start(context);
        long startNanos = System.nanoTime();

        try {
            ResiliencePolicy policy = policyProvider.resolve(dependencyKey);
            applyRateLimit(policy, context);

            CircuitBreakerRuntime circuitBreaker = circuitBreakers.computeIfAbsent(
                    dependencyKey,
                    ignored -> new CircuitBreakerRuntime()
            );

            int retryCount = 0;

            while (true) {
                long retryAfterSeconds = circuitBreaker.beforeCall(policy.circuitBreaker(), clock.now());
                telemetry.recordCircuitState(dependencyKey, circuitBreaker.currentState());

                if (retryAfterSeconds > 0) {
                    telemetry.recordRequest(dependencyKey, operationName, REQUEST_OUTCOME_UPSTREAM_ERROR);
                    throw new CircuitBreakerOpenException(dependencyKey, retryAfterSeconds);
                }

                try {
                    T result = executeWithTimeout(action, policy.timeout());

                    circuitBreaker.recordSuccess(policy.circuitBreaker());
                    telemetry.recordCircuitState(dependencyKey, circuitBreaker.currentState());
                    telemetry.recordRequest(dependencyKey, operationName, REQUEST_OUTCOME_SUCCESS);

                    telemetryContext.attribute("retry.count", String.valueOf(retryCount));
                    telemetryContext.attribute(
                            "cb.state",
                            circuitBreaker.currentState().name().toLowerCase(Locale.ROOT)
                    );

                    return result;
                } catch (RuntimeException ex) {
                    Throwable root = ErrorClassifier.unwrap(ex);
                    ErrorCategory category = ErrorClassifier.classify(root);
                    boolean circuitFailure = isCircuitFailure(category);

                    telemetryContext.attribute("error.category", category.name().toLowerCase(Locale.ROOT));
                    recordUpstreamStatusCode(telemetryContext, root);

                    if (circuitFailure) {
                        circuitBreaker.recordFailure(policy.circuitBreaker(), clock.now());
                        telemetry.recordCircuitState(dependencyKey, circuitBreaker.currentState());
                    } else {
                        circuitBreaker.releaseHalfOpenSlotIfNecessary();
                    }

                    if (isRetryable(category) && retryCount < policy.retry().maxRetries()) {
                        retryCount++;
                        telemetry.recordRetry(dependencyKey, operationName);

                        Duration delay = computeRetryDelay(policy.retry(), retryCount);
                        sleep(delay);
                        continue;
                    }

                    telemetry.recordRequest(dependencyKey, operationName, outcomeFor(category));
                    throw propagate(root);
                }
            }
        } finally {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            telemetry.recordLatency(dependencyKey, operationName, latencyMs);
            telemetryContext.close();
        }
    }

    private void applyRateLimit(ResiliencePolicy policy, RequestContext context) {
        String clientKey = context.clientKey();
        if (clientKey == null || clientKey.isBlank()) {
            return;
        }

        String bucketKey = context.dependencyKey() + "|" + clientKey;
        RateLimitDecision decision = rateLimiter.tryAcquire(bucketKey, policy.rateLimit(), clock);

        if (!decision.allowed()) {
            telemetry.recordRateLimited(context.dependencyKey(), context.operationName(), clientKey);
            telemetry.recordRequest(context.dependencyKey(), context.operationName(), REQUEST_OUTCOME_RATE_LIMITED);
            throw new RateLimitExceededException(
                    "Rate limit exceeded for client key " + clientKey,
                    decision.retryAfterSeconds()
            );
        }
    }

    private void recordUpstreamStatusCode(Telemetry.TelemetryContext telemetryContext, Throwable root) {
        if (root instanceof UpstreamException upstreamException && upstreamException.getStatusCode() != null) {
            telemetryContext.attribute("http.status_code", String.valueOf(upstreamException.getStatusCode()));
        }
    }

    private <T> T executeWithTimeout(Supplier<T> action, ResiliencePolicy.TimeoutPolicy timeoutPolicy) {
        Future<T> future = timeoutExecutor.submit(action::get);
        try {
            return future.get(timeoutPolicy.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw UpstreamException.timeout("Request timed out", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw UpstreamException.timeout("Request interrupted", ex);
        } catch (Exception ex) {
            Throwable root = ErrorClassifier.unwrap(ex);
            throw propagate(root);
        }
    }

    private void sleep(Duration duration) {
        try {
            clock.sleep(duration);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw UpstreamException.timeout("Retry sleep interrupted", ex);
        }
    }

    private Duration computeRetryDelay(ResiliencePolicy.RetryPolicy retryPolicy, int retryCount) {
        long exponent = Math.max(0L, retryCount - 1L);
        long baseMillis = Math.min(
                retryPolicy.maxBackoff().toMillis(),
                Math.round(retryPolicy.initialBackoff().toMillis() * Math.pow(2.0d, exponent))
        );

        double jitter = baseMillis * retryPolicy.jitterFactor();
        if (jitter == 0.0d) {
            return Duration.ofMillis(baseMillis);
        }
        double jittered = baseMillis + ThreadLocalRandom.current().nextDouble(-jitter, jitter);
        long finalMillis = Math.max(0L, Math.round(jittered));
        return Duration.ofMillis(finalMillis);
    }

    private boolean isRetryable(ErrorCategory category) {
        return category == ErrorCategory.SERVER_ERROR
                || category == ErrorCategory.TIMEOUT
                || category == ErrorCategory.NETWORK;
    }

    private boolean isCircuitFailure(ErrorCategory category) {
        return category == ErrorCategory.SERVER_ERROR
                || category == ErrorCategory.TIMEOUT
                || category == ErrorCategory.NETWORK;
    }

    private String outcomeFor(ErrorCategory category) {
        return switch (category) {
            case SERVER_ERROR, TIMEOUT, NETWORK, CIRCUIT_OPEN -> REQUEST_OUTCOME_UPSTREAM_ERROR;
            case RATE_LIMITED -> REQUEST_OUTCOME_RATE_LIMITED;
            case CLIENT_ERROR -> REQUEST_OUTCOME_CLIENT_ERROR;
            case UNKNOWN -> REQUEST_OUTCOME_UNKNOWN_ERROR;
        };
    }

    private RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(throwable);
    }

    @Override
    public void close() {
        timeoutExecutor.shutdown();
    }

    private static final class CircuitBreakerRuntime {
        private CircuitState state = CircuitState.CLOSED;
        private int consecutiveFailures;
        private Instant openedAt;
        private int halfOpenSuccesses;
        private int halfOpenInFlight;

        private synchronized long beforeCall(ResiliencePolicy.CircuitBreakerPolicy policy, Instant now) {
            if (state == CircuitState.OPEN) {
                Duration elapsed = Duration.between(openedAt, now);
                if (!elapsed.isNegative() && elapsed.compareTo(policy.halfOpenAfter()) >= 0) {
                    state = CircuitState.HALF_OPEN;
                    halfOpenSuccesses = 0;
                    halfOpenInFlight = 0;
                } else {
                    Duration remaining = policy.halfOpenAfter().minus(elapsed);
                    return Math.max(1L, remaining.toSeconds());
                }
            }

            if (state == CircuitState.HALF_OPEN) {
                if (halfOpenInFlight >= policy.halfOpenMaxCalls()) {
                    return 1L;
                }
                halfOpenInFlight++;
            }
            return 0L;
        }

        private synchronized void recordSuccess(ResiliencePolicy.CircuitBreakerPolicy policy) {
            if (state == CircuitState.HALF_OPEN) {
                halfOpenInFlight = Math.max(0, halfOpenInFlight - 1);
                halfOpenSuccesses++;
                if (halfOpenSuccesses >= policy.halfOpenMaxCalls()) {
                    close();
                }
                return;
            }

            consecutiveFailures = 0;
        }

        private synchronized void recordFailure(ResiliencePolicy.CircuitBreakerPolicy policy, Instant now) {
            if (state == CircuitState.HALF_OPEN) {
                halfOpenInFlight = Math.max(0, halfOpenInFlight - 1);
                open(now);
                return;
            }

            consecutiveFailures++;
            if (consecutiveFailures >= policy.failureThreshold()) {
                open(now);
            }
        }

        private synchronized CircuitState currentState() {
            return state;
        }

        private synchronized void releaseHalfOpenSlotIfNecessary() {
            if (state == CircuitState.HALF_OPEN && halfOpenInFlight > 0) {
                halfOpenInFlight--;
            }
        }

        private void open(Instant now) {
            state = CircuitState.OPEN;
            openedAt = now;
            consecutiveFailures = 0;
            halfOpenSuccesses = 0;
            halfOpenInFlight = 0;
        }

        private void close() {
            state = CircuitState.CLOSED;
            openedAt = null;
            consecutiveFailures = 0;
            halfOpenSuccesses = 0;
            halfOpenInFlight = 0;
        }
    }
}
