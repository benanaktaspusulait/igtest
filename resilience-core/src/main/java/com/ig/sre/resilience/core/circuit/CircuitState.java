package com.ig.sre.resilience.core.circuit;

public enum CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN
}
