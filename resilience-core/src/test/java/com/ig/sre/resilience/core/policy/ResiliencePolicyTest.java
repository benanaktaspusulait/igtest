package com.ig.sre.resilience.core.policy;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResiliencePolicyTest {

    @Test
    void defaultsAreInitializedAsExpected() {
        ResiliencePolicy defaults = ResiliencePolicy.defaults();

        assertThat(defaults.timeout().timeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(defaults.retry().maxRetries()).isEqualTo(3);
        assertThat(defaults.circuitBreaker().failureThreshold()).isEqualTo(5);
        assertThat(defaults.rateLimit().permitsPerMinute()).isEqualTo(100);
    }

    @Test
    void topLevelPolicyRejectsNullComponents() {
        ResiliencePolicy.TimeoutPolicy timeout = new ResiliencePolicy.TimeoutPolicy(Duration.ofSeconds(1));
        ResiliencePolicy.RetryPolicy retry =
                new ResiliencePolicy.RetryPolicy(1, Duration.ofMillis(100), Duration.ofMillis(200), 0.2);
        ResiliencePolicy.CircuitBreakerPolicy breaker =
                new ResiliencePolicy.CircuitBreakerPolicy(1, Duration.ofSeconds(10), 1);
        ResiliencePolicy.RateLimitPolicy rateLimit = new ResiliencePolicy.RateLimitPolicy(10);

        assertThatThrownBy(() -> new ResiliencePolicy(null, retry, breaker, rateLimit))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ResiliencePolicy(timeout, null, breaker, rateLimit))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ResiliencePolicy(timeout, retry, null, rateLimit))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ResiliencePolicy(timeout, retry, breaker, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void timeoutPolicyRejectsNonPositiveDuration() {
        Duration zero = Duration.ZERO;
        Duration negativeOneMillis = Duration.ofMillis(-1);

        assertThatThrownBy(() -> new ResiliencePolicy.TimeoutPolicy(zero))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResiliencePolicy.TimeoutPolicy(negativeOneMillis))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retryPolicyRejectsInvalidValues() {
        Duration tenMillis = Duration.ofMillis(10);
        Duration twentyMillis = Duration.ofMillis(20);
        Duration zero = Duration.ZERO;
        double validJitter = 0.1;

        assertThatThrownBy(() -> new ResiliencePolicy.RetryPolicy(-1, tenMillis, twentyMillis, validJitter))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResiliencePolicy.RetryPolicy(1, zero, twentyMillis, validJitter))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResiliencePolicy.RetryPolicy(1, twentyMillis, tenMillis, validJitter))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResiliencePolicy.RetryPolicy(1, tenMillis, twentyMillis, 1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void circuitBreakerPolicyRejectsInvalidValues() {
        Duration tenSeconds = Duration.ofSeconds(10);
        Duration zero = Duration.ZERO;

        assertThatThrownBy(() -> new ResiliencePolicy.CircuitBreakerPolicy(0, tenSeconds, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResiliencePolicy.CircuitBreakerPolicy(1, zero, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResiliencePolicy.CircuitBreakerPolicy(1, tenSeconds, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rateLimitPolicyRejectsNonPositivePermits() {
        assertThatThrownBy(() -> new ResiliencePolicy.RateLimitPolicy(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
