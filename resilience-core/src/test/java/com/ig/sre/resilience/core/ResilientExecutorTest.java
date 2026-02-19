package com.ig.sre.resilience.core;

import com.ig.sre.resilience.core.circuit.CircuitBreakerOpenException;
import com.ig.sre.resilience.core.clock.ResilienceClock;
import com.ig.sre.resilience.core.context.RequestContext;
import com.ig.sre.resilience.core.error.UpstreamException;
import com.ig.sre.resilience.core.executor.ResilientExecutor;
import com.ig.sre.resilience.core.policy.ResiliencePolicy;
import com.ig.sre.resilience.core.ratelimit.InMemoryTokenBucketRateLimiter;
import com.ig.sre.resilience.core.ratelimit.RateLimitExceededException;
import com.ig.sre.resilience.core.telemetry.NoOpTelemetry;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientExecutorTest {

    private static final int DEFAULT_FAILURE_THRESHOLD = 5;
    private static final long DEFAULT_HALF_OPEN_AFTER_SECONDS = 30;
    private static final int DEFAULT_HALF_OPEN_MAX_CALLS = 2;

    @Test
    void doesNotRetryOnClientErrors() {
        MutableClock clock = new MutableClock();
        ResiliencePolicy policy = policyWith(3, 1000, 4000, 2000, 100);
        RequestContext requestContext = new RequestContext("tfl", "lineStatus", "ip-1");
        AtomicInteger attempts;
        try (ResilientExecutor executor = new ResilientExecutor(
                ignored -> policy,
                new InMemoryTokenBucketRateLimiter(),
                new NoOpTelemetry(),
                clock
        )) {

            attempts = new AtomicInteger();
            Supplier<String> action = () -> {
                attempts.incrementAndGet();
                throw UpstreamException.clientError(400, "bad request");
            };

            Assertions.assertThatThrownBy(() -> executor.execute(action, requestContext))
                    .isInstanceOf(UpstreamException.class);
        }
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void retriesTransientErrorsAndEventuallySucceeds() {
        MutableClock clock = new MutableClock();
        ResiliencePolicy policy = policyWith(3, 100, 300, 2000, 100);
        RequestContext requestContext = new RequestContext("tfl", "lineStatus", "ip-1");
        AtomicInteger attempts;
        String result;
        try (ResilientExecutor executor = new ResilientExecutor(
                ignored -> policy,
                new InMemoryTokenBucketRateLimiter(),
                new NoOpTelemetry(),
                clock
        )) {

            attempts = new AtomicInteger();
            Supplier<String> action = () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                    throw UpstreamException.serverError(503, "upstream unavailable");
                }
                return "ok";
            };

            result = executor.execute(action, requestContext);
        }

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void opensCircuitAfterConsecutiveFailures() {
        MutableClock clock = new MutableClock();
        ResiliencePolicy policy = policyWith(0, 100, 100, 2000, 100);
        RequestContext requestContext = new RequestContext("tfl", "lineStatus", "ip-1");
        try (ResilientExecutor executor = new ResilientExecutor(
                ignored -> policy,
                new InMemoryTokenBucketRateLimiter(),
                new NoOpTelemetry(),
                clock
        )) {

            Supplier<String> action = () -> {
                throw UpstreamException.serverError(503, "upstream unavailable");
            };

            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> executor.execute(action, requestContext))
                        .isInstanceOf(UpstreamException.class);
            }

            assertThatThrownBy(() -> executor.execute(action, requestContext))
                    .isInstanceOf(CircuitBreakerOpenException.class);
        }
    }

    @Test
    void rateLimiterBlocksWhenQuotaIsExceeded() {
        MutableClock clock = new MutableClock();
        ResiliencePolicy policy = policyWith(0, 100, 100, 2000, 1);
        RequestContext requestContext = new RequestContext("tfl", "lineStatus", "ip-1");
        try (ResilientExecutor executor = new ResilientExecutor(
                ignored -> policy,
                new InMemoryTokenBucketRateLimiter(),
                new NoOpTelemetry(),
                clock
        )) {

            Supplier<String> action = () -> "ok";

            assertThat(executor.execute(action, requestContext)).isEqualTo("ok");
            assertThatThrownBy(() -> executor.execute(action, requestContext))
                    .isInstanceOf(RateLimitExceededException.class);
        }
    }

    @Test
    void timesOutAndThrowsTimeoutUpstreamException() {
        MutableClock clock = new MutableClock();
        ResiliencePolicy policy = policyWith(0, 100, 100, 10, 100);
        RequestContext requestContext = new RequestContext("tfl", "lineStatus", "ip-1");
        try (ResilientExecutor executor = new ResilientExecutor(
                ignored -> policy,
                new InMemoryTokenBucketRateLimiter(),
                new NoOpTelemetry(),
                clock
        )) {
            AtomicBoolean interrupted = new AtomicBoolean(false);
            Supplier<String> action = () -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ex) {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                }
                return "late";
            };

            assertThatThrownBy(() -> executor.execute(action, requestContext))
                    .isInstanceOf(UpstreamException.class)
                    .extracting(ex -> ((UpstreamException) ex).getCategory())
                    .isEqualTo(com.ig.sre.resilience.core.error.ErrorCategory.TIMEOUT);
            assertThat(interrupted.get()).isTrue();
        }
    }

    @Test
    void recoversFromOpenCircuitAfterHalfOpenWindow() {
        MutableClock clock = new MutableClock();
        ResiliencePolicy policy = policyWith(0, 100, 100, 2000, 100);
        RequestContext requestContext = new RequestContext("tfl", "lineStatus", "ip-1");
        AtomicInteger attempts = new AtomicInteger();

        try (ResilientExecutor executor = new ResilientExecutor(
                ignored -> policy,
                new InMemoryTokenBucketRateLimiter(),
                new NoOpTelemetry(),
                clock
        )) {
            Supplier<String> action = () -> {
                int current = attempts.incrementAndGet();
                if (current <= 5) {
                    throw UpstreamException.serverError(503, "upstream unavailable");
                }
                return "ok";
            };

            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> executor.execute(action, requestContext))
                        .isInstanceOf(UpstreamException.class);
            }

            assertThatThrownBy(() -> executor.execute(action, requestContext))
                    .isInstanceOf(CircuitBreakerOpenException.class);

            clock.sleep(Duration.ofSeconds(31));

            assertThat(executor.execute(action, requestContext)).isEqualTo("ok");
            assertThat(executor.execute(action, requestContext)).isEqualTo("ok");
            assertThat(executor.execute(action, requestContext)).isEqualTo("ok");
        }

        assertThat(attempts.get()).isEqualTo(8);
    }

    @Test
    void halfOpenRejectsConcurrentProbesBeyondMaxCalls() throws Exception {
        MutableClock clock = new MutableClock();
        ResiliencePolicy policy = new ResiliencePolicy(
                new ResiliencePolicy.TimeoutPolicy(Duration.ofSeconds(5)),
                new ResiliencePolicy.RetryPolicy(0, Duration.ofMillis(100), Duration.ofMillis(100), 0.0d),
                new ResiliencePolicy.CircuitBreakerPolicy(1, Duration.ofSeconds(1), 1),
                new ResiliencePolicy.RateLimitPolicy(100)
        );
        RequestContext requestContext = new RequestContext("tfl", "lineStatus", "ip-1");

        try (ResilientExecutor executor = new ResilientExecutor(
                ignored -> policy,
                new InMemoryTokenBucketRateLimiter(),
                new NoOpTelemetry(),
                clock
        )) {
            assertThatThrownBy(() -> executor.execute(
                    () -> {
                        throw UpstreamException.serverError(503, "upstream unavailable");
                    },
                    requestContext
            )).isInstanceOf(UpstreamException.class);

            clock.sleep(Duration.ofSeconds(2));

            CountDownLatch probeStarted = new CountDownLatch(1);
            CountDownLatch releaseProbe = new CountDownLatch(1);
            ExecutorService callers = Executors.newSingleThreadExecutor();
            try {
                Future<String> firstProbe = callers.submit(() -> executor.execute(() -> {
                    probeStarted.countDown();
                    try {
                        releaseProbe.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ex);
                    }
                    return "ok";
                }, requestContext));

                assertThat(probeStarted.await(1, TimeUnit.SECONDS)).isTrue();

                assertThatThrownBy(() -> executor.execute(() -> "second-probe", requestContext))
                        .isInstanceOf(CircuitBreakerOpenException.class);

                releaseProbe.countDown();
                assertThat(firstProbe.get(2, TimeUnit.SECONDS)).isEqualTo("ok");
                assertThat(executor.execute(() -> "after-recovery", requestContext)).isEqualTo("after-recovery");
            } finally {
                releaseProbe.countDown();
                callers.shutdownNow();
            }
        }
    }

    private ResiliencePolicy policyWith(
            int maxRetries,
            long initialBackoffMs,
            long maxBackoffMs,
            long timeoutMs,
            int permitsPerMinute
    ) {
        return new ResiliencePolicy(
                new ResiliencePolicy.TimeoutPolicy(Duration.ofMillis(timeoutMs)),
                new ResiliencePolicy.RetryPolicy(
                        maxRetries,
                        Duration.ofMillis(initialBackoffMs),
                        Duration.ofMillis(maxBackoffMs),
                        0.0
                ),
                new ResiliencePolicy.CircuitBreakerPolicy(
                        DEFAULT_FAILURE_THRESHOLD,
                        Duration.ofSeconds(DEFAULT_HALF_OPEN_AFTER_SECONDS),
                        DEFAULT_HALF_OPEN_MAX_CALLS
                ),
                new ResiliencePolicy.RateLimitPolicy(permitsPerMinute)
        );
    }

    private static final class MutableClock implements ResilienceClock {
        private Instant now = Instant.parse("2026-02-19T10:00:00Z");

        @Override
        public Instant now() {
            return now;
        }

        @Override
        public void sleep(Duration duration) {
            now = now.plus(duration);
        }
    }
}
