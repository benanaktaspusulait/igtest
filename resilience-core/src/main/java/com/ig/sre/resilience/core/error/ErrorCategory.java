package com.ig.sre.resilience.core.error;

public enum ErrorCategory {
    CLIENT_ERROR,
    SERVER_ERROR,
    TIMEOUT,
    NETWORK,
    RATE_LIMITED,
    CIRCUIT_OPEN,
    UNKNOWN
}
