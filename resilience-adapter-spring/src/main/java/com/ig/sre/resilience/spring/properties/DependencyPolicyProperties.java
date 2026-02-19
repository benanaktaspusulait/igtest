package com.ig.sre.resilience.spring.properties;

import com.ig.sre.resilience.core.policy.ResiliencePolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

public record DependencyPolicyProperties(
        @Positive @DefaultValue("2000") long timeoutMs,
        @Valid @DefaultValue RetryProperties retry,
        @Valid @DefaultValue CircuitBreakerProperties circuitBreaker,
        @Valid @DefaultValue RateLimitProperties rateLimit
) {

    public ResiliencePolicy toPolicy() {
        return new ResiliencePolicy(
                new ResiliencePolicy.TimeoutPolicy(Duration.ofMillis(timeoutMs)),
                new ResiliencePolicy.RetryPolicy(
                        retry.maxRetries(),
                        Duration.ofMillis(retry.initialBackoffMs()),
                        Duration.ofMillis(retry.maxBackoffMs()),
                        retry.jitterFactor()
                ),
                new ResiliencePolicy.CircuitBreakerPolicy(
                        circuitBreaker.failureThreshold(),
                        Duration.ofSeconds(circuitBreaker.halfOpenAfterSeconds()),
                        circuitBreaker.halfOpenMaxCalls()
                ),
                new ResiliencePolicy.RateLimitPolicy(rateLimit.permitsPerMinute())
        );
    }

}
