package com.ig.sre.resilience.spring.properties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.bind.DefaultValue;

public record RetryProperties(
        @Positive @DefaultValue("3") int maxRetries,
        @Positive @DefaultValue("1000") long initialBackoffMs,
        @Positive @DefaultValue("4000") long maxBackoffMs,
        @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.25") double jitterFactor
) {
}
