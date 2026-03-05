package com.ig.sre.resilience.spring.properties;

import com.ig.sre.resilience.core.policy.ResiliencePolicy;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpringResiliencePropertiesTest {

    @Test
    void mapsDependencyPoliciesToCorePolicyModel() {
        SpringResilienceProperties properties = getSpringResilienceProperties();

        Map<String, ResiliencePolicy> mapped = properties.toPolicies();

        assertThat(mapped).containsOnlyKeys("dependency-a");
        ResiliencePolicy policy = mapped.get("dependency-a");
        assertThat(policy.timeout().timeout()).isEqualTo(Duration.ofMillis(2500));
        assertThat(policy.retry().maxRetries()).isEqualTo(4);
        assertThat(policy.retry().initialBackoff()).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.retry().maxBackoff()).isEqualTo(Duration.ofMillis(800));
        assertThat(policy.retry().jitterFactor()).isEqualTo(0.2);
        assertThat(policy.circuitBreaker().failureThreshold()).isEqualTo(7);
        assertThat(policy.circuitBreaker().halfOpenAfter()).isEqualTo(Duration.ofSeconds(45));
        assertThat(policy.circuitBreaker().halfOpenMaxCalls()).isEqualTo(3);
        assertThat(policy.rateLimit().permitsPerMinute()).isEqualTo(120);
    }

    private static @NonNull SpringResilienceProperties getSpringResilienceProperties() {
        RetryProperties retryProperties = new RetryProperties(4, 200, 800, 0.2);
        CircuitBreakerProperties circuitBreakerProperties = new CircuitBreakerProperties(7, 45, 3);
        RateLimitProperties rateLimitProperties = new RateLimitProperties(120);
        DependencyPolicyProperties dependencyPolicyProperties = new DependencyPolicyProperties(
                2500,
                retryProperties,
                circuitBreakerProperties,
                rateLimitProperties
        );

        return new SpringResilienceProperties(Map.of("dependency-a", dependencyPolicyProperties));
    }

    @Test
    void supportsRecordDefaultValues() {
        ResiliencePolicy policy = getResiliencePolicy();

        assertThat(policy.timeout().timeout()).isEqualTo(Duration.ofMillis(2000));
        assertThat(policy.retry().maxRetries()).isEqualTo(3);
        assertThat(policy.retry().initialBackoff()).isEqualTo(Duration.ofMillis(1000));
        assertThat(policy.retry().maxBackoff()).isEqualTo(Duration.ofMillis(4000));
        assertThat(policy.retry().jitterFactor()).isEqualTo(0.25);
        assertThat(policy.circuitBreaker().failureThreshold()).isEqualTo(5);
        assertThat(policy.circuitBreaker().halfOpenAfter()).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.circuitBreaker().halfOpenMaxCalls()).isEqualTo(2);
        assertThat(policy.rateLimit().permitsPerMinute()).isEqualTo(100);
    }

    private static @NonNull ResiliencePolicy getResiliencePolicy() {
        RetryProperties retryProperties = new RetryProperties(3, 1000, 4000, 0.25);
        CircuitBreakerProperties circuitBreakerProperties = new CircuitBreakerProperties(5, 30, 2);
        RateLimitProperties rateLimitProperties = new RateLimitProperties(100);
        DependencyPolicyProperties defaults = new DependencyPolicyProperties(
                2000,
                retryProperties,
                circuitBreakerProperties,
                rateLimitProperties
        );

        ResiliencePolicy policy = defaults.toPolicy();
        return policy;
    }
}
