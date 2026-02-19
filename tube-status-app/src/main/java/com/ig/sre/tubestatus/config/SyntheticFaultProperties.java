package com.ig.sre.tubestatus.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record SyntheticFaultProperties(
        boolean enabled,
        @DecimalMin("0.0") @DecimalMax("1.0") double serverErrorRate,
        @DecimalMin("0.0") @DecimalMax("1.0") double timeoutRate
) {
    public static final SyntheticFaultProperties DISABLED = new SyntheticFaultProperties(false, 0.0d, 0.0d);
}
