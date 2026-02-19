package com.ig.sre.resilience.spring.properties;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.bind.DefaultValue;

public record RateLimitProperties(@Positive @DefaultValue("100") int permitsPerMinute) {
}
