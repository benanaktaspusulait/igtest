package com.ig.sre.resilience.core.executor;

import com.ig.sre.resilience.core.clock.ResilienceClock;
import com.ig.sre.resilience.core.context.RequestContext;
import com.ig.sre.resilience.core.error.UpstreamException;
import com.ig.sre.resilience.core.policy.ResiliencePolicy;
import com.ig.sre.resilience.core.ratelimit.InMemoryTokenBucketRateLimiter;
import com.ig.sre.resilience.core.ratelimit.RateLimitExceededException;
import com.ig.sre.resilience.core.telemetry.Telemetry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientExecutorOutcomeTest {

    private static final String DEPENDENCY_KEY = "tfl";
    private static final String OPERATION_NAME = "line_status";
    private static final String CLIENT_KEY = "ip-1";

    @Test
    void recordsUpstreamErrorOutcomeForServerFailures() {
        CapturingTelemetry telemetry = new CapturingTelemetry();
        try (ResilientExecutor executor = new ResilientExecutor(
                ignored -> policy(100),
                new InMemoryTokenBucketRateLimiter(),
                telemetry,
                new FixedClock()
        )) {
            assertThatThrownBy(() -> executor.execute(
                    () -> {
                        throw UpstreamException.serverError(503, "upstream unavailable");
                    },
                    new RequestContext(DEPENDENCY_KEY, OPERATION_NAME, CLIENT_KEY)
            )).isInstanceOf(UpstreamException.class);
        }

        assertThat(telemetry.requestOutcomes()).containsExactly("upstream_error");
    }

    @Test
    void recordsClientErrorOutcomeSeparately() {
        CapturingTelemetry telemetry = new CapturingTelemetry();
        try (ResilientExecutor executor = new ResilientExecutor(
                ignored -> policy(100),
                new InMemoryTokenBucketRateLimiter(),
                telemetry,
                new FixedClock()
        )) {
            assertThatThrownBy(() -> executor.execute(
                    () -> {
                        throw UpstreamException.clientError(400, "bad request");
                    },
                    new RequestContext(DEPENDENCY_KEY, OPERATION_NAME, CLIENT_KEY)
            )).isInstanceOf(UpstreamException.class);
        }

        assertThat(telemetry.requestOutcomes()).containsExactly("client_error");
    }

    @Test
    void recordsRateLimitedOutcomeSeparately() {
        CapturingTelemetry telemetry = new CapturingTelemetry();
        try (ResilientExecutor executor = new ResilientExecutor(
                ignored -> policy(1),
                new InMemoryTokenBucketRateLimiter(),
                telemetry,
                new FixedClock()
        )) {
            RequestContext context = new RequestContext(DEPENDENCY_KEY, OPERATION_NAME, CLIENT_KEY);
            assertThat(executor.execute(() -> "ok", context)).isEqualTo("ok");
            assertThatThrownBy(() -> executor.execute(() -> "limited", context))
                    .isInstanceOf(RateLimitExceededException.class);
        }

        assertThat(telemetry.requestOutcomes()).containsExactly("success", "rate_limited");
    }

    private ResiliencePolicy policy(int permitsPerMinute) {
        return new ResiliencePolicy(
                new ResiliencePolicy.TimeoutPolicy(Duration.ofSeconds(2)),
                new ResiliencePolicy.RetryPolicy(0, Duration.ofMillis(100), Duration.ofMillis(100), 0.0d),
                new ResiliencePolicy.CircuitBreakerPolicy(5, Duration.ofSeconds(30), 2),
                new ResiliencePolicy.RateLimitPolicy(permitsPerMinute)
        );
    }

    private static final class FixedClock implements ResilienceClock {
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

    private static final class CapturingTelemetry implements Telemetry {
        private final List<String> requestOutcomes = new CopyOnWriteArrayList<>();

        @Override
        public TelemetryContext start(RequestContext context) {
            return new TelemetryContext() {
                @Override
                public void attribute(String key, String value) {
                    // no-op for tests
                }

                @Override
                public void close() {
                    // no-op for tests
                }
            };
        }

        @Override
        public void recordRequest(String dependencyKey, String operationName, String outcome) {
            requestOutcomes.add(outcome);
        }

        @Override
        public void recordLatency(String dependencyKey, String operationName, long latencyMs) {
            // no-op for tests
        }

        @Override
        public void recordRetry(String dependencyKey, String operationName) {
            // no-op for tests
        }

        @Override
        public void recordCircuitState(String dependencyKey, com.ig.sre.resilience.core.circuit.CircuitState state) {
            // no-op for tests
        }

        @Override
        public void recordRateLimited(String dependencyKey, String operationName, String clientKey) {
            // no-op for tests
        }

        private List<String> requestOutcomes() {
            return requestOutcomes;
        }
    }
}
