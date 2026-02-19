package com.ig.sre.resilience.core.policy;

import java.time.Duration;
import java.util.Objects;

public record ResiliencePolicy(
        TimeoutPolicy timeout,
        RetryPolicy retry,
        CircuitBreakerPolicy circuitBreaker,
        RateLimitPolicy rateLimit
) {
    public ResiliencePolicy {
        Objects.requireNonNull(timeout, "timeout must not be null");
        Objects.requireNonNull(retry, "retry must not be null");
        Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
        Objects.requireNonNull(rateLimit, "rateLimit must not be null");
    }

    public static ResiliencePolicy defaults() {
        return new ResiliencePolicy(
                new TimeoutPolicy(Duration.ofSeconds(2)),
                new RetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(4), 0.25),
                new CircuitBreakerPolicy(5, Duration.ofSeconds(30), 2),
                new RateLimitPolicy(100)
        );
    }

    public record TimeoutPolicy(Duration timeout) {
        public TimeoutPolicy {
            Objects.requireNonNull(timeout, "timeout must not be null");
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
        }
    }

    public record RetryPolicy(int maxRetries, Duration initialBackoff, Duration maxBackoff, double jitterFactor) {
        public RetryPolicy {
            Objects.requireNonNull(initialBackoff, "initialBackoff must not be null");
            Objects.requireNonNull(maxBackoff, "maxBackoff must not be null");

            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be >= 0");
            }
            if (initialBackoff.isZero() || initialBackoff.isNegative()) {
                throw new IllegalArgumentException("initialBackoff must be positive");
            }
            if (maxBackoff.isZero() || maxBackoff.isNegative()) {
                throw new IllegalArgumentException("maxBackoff must be positive");
            }
            if (maxBackoff.compareTo(initialBackoff) < 0) {
                throw new IllegalArgumentException("maxBackoff must be >= initialBackoff");
            }
            if (jitterFactor < 0.0 || jitterFactor > 1.0) {
                throw new IllegalArgumentException("jitterFactor must be between 0.0 and 1.0");
            }
        }
    }

    public record CircuitBreakerPolicy(int failureThreshold, Duration halfOpenAfter, int halfOpenMaxCalls) {
        public CircuitBreakerPolicy {
            Objects.requireNonNull(halfOpenAfter, "halfOpenAfter must not be null");

            if (failureThreshold <= 0) {
                throw new IllegalArgumentException("failureThreshold must be > 0");
            }
            if (halfOpenAfter.isZero() || halfOpenAfter.isNegative()) {
                throw new IllegalArgumentException("halfOpenAfter must be positive");
            }
            if (halfOpenMaxCalls <= 0) {
                throw new IllegalArgumentException("halfOpenMaxCalls must be > 0");
            }
        }
    }

    public record RateLimitPolicy(int permitsPerMinute) {
        public RateLimitPolicy {
            if (permitsPerMinute <= 0) {
                throw new IllegalArgumentException("permitsPerMinute must be > 0");
            }
        }
    }
}
