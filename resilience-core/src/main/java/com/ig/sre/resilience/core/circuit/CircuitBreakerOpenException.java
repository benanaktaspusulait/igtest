package com.ig.sre.resilience.core.circuit;

import java.io.Serial;

public class CircuitBreakerOpenException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    public CircuitBreakerOpenException(String dependencyKey, long retryAfterSeconds) {
        super("Circuit breaker is open for dependency: " + dependencyKey);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

}
