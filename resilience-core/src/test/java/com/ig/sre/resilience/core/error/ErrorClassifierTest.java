package com.ig.sre.resilience.core.error;

import com.ig.sre.resilience.core.circuit.CircuitBreakerOpenException;
import com.ig.sre.resilience.core.ratelimit.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorClassifierTest {

    @Test
    void unwrapReturnsDeepestCauseForWrappedExceptions() {
        IllegalStateException root = new IllegalStateException("boom");
        ExecutionException executionException = new ExecutionException(root);
        CompletionException completionException = new CompletionException(executionException);

        assertThat(ErrorClassifier.unwrap(completionException)).isSameAs(root);
    }

    @Test
    void classifyReturnsUpstreamCategory() {
        assertThat(ErrorClassifier.classify(UpstreamException.clientError(400, "bad")))
                .isEqualTo(ErrorCategory.CLIENT_ERROR);
        assertThat(ErrorClassifier.classify(UpstreamException.serverError(503, "unavailable")))
                .isEqualTo(ErrorCategory.SERVER_ERROR);
    }

    @Test
    void classifyCircuitAndRateLimitErrors() {
        assertThat(ErrorClassifier.classify(new CircuitBreakerOpenException("tfl", 10)))
                .isEqualTo(ErrorCategory.CIRCUIT_OPEN);
        assertThat(ErrorClassifier.classify(new RateLimitExceededException("limit", 1)))
                .isEqualTo(ErrorCategory.RATE_LIMITED);
    }

    @Test
    void classifyTimeoutErrors() {
        assertThat(ErrorClassifier.classify(new TimeoutException("timeout"))).isEqualTo(ErrorCategory.TIMEOUT);
        assertThat(ErrorClassifier.classify(new HttpTimeoutException("http-timeout"))).isEqualTo(ErrorCategory.TIMEOUT);
        assertThat(ErrorClassifier.classify(new InterruptedIOException("io-timeout"))).isEqualTo(ErrorCategory.TIMEOUT);
    }

    @Test
    void classifyNetworkAndUnknownErrors() {
        assertThat(ErrorClassifier.classify(new IOException("io"))).isEqualTo(ErrorCategory.NETWORK);
        assertThat(ErrorClassifier.classify(new IllegalArgumentException("bad"))).isEqualTo(ErrorCategory.UNKNOWN);
    }
}
