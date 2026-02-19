package com.ig.sre.resilience.core.context;

public record RequestContext(
        String dependencyKey,
        String operationName,
        String clientKey
) {
    public RequestContext {
        if (dependencyKey == null || dependencyKey.isBlank()) {
            throw new IllegalArgumentException("dependencyKey must not be blank");
        }
        if (operationName == null || operationName.isBlank()) {
            throw new IllegalArgumentException("operationName must not be blank");
        }
    }

    public String clientKeyOrAnonymous() {
        return (clientKey == null || clientKey.isBlank()) ? "anonymous" : clientKey;
    }
}
