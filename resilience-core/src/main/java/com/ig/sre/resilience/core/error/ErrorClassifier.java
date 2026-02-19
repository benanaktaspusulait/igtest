package com.ig.sre.resilience.core.error;

import com.ig.sre.resilience.core.circuit.CircuitBreakerOpenException;
import com.ig.sre.resilience.core.ratelimit.RateLimitExceededException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public final class ErrorClassifier {

    private ErrorClassifier() {
    }

    public static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
            if (current.getCause() == null) {
                break;
            }
            current = current.getCause();
        }
        return current;
    }

    public static ErrorCategory classify(Throwable throwable) {
        Throwable root = unwrap(throwable);

        if (root instanceof UpstreamException upstreamException) {
            return upstreamException.getCategory();
        }
        if (root instanceof CircuitBreakerOpenException) {
            return ErrorCategory.CIRCUIT_OPEN;
        }
        if (root instanceof RateLimitExceededException) {
            return ErrorCategory.RATE_LIMITED;
        }
        if (root instanceof TimeoutException
                || root instanceof HttpTimeoutException
                || root instanceof InterruptedIOException) {
            return ErrorCategory.TIMEOUT;
        }
        if (root instanceof IOException) {
            return ErrorCategory.NETWORK;
        }

        return ErrorCategory.UNKNOWN;
    }
}
