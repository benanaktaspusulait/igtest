package com.ig.sre.resilience.spring.properties;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.bind.DefaultValue;

public record CircuitBreakerProperties(
        @Positive @DefaultValue("5") int failureThreshold,
        @Positive @DefaultValue("30") long halfOpenAfterSeconds,
        @Positive @DefaultValue("2") int halfOpenMaxCalls
) {
}
