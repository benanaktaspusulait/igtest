package com.ig.sre.resilience.core.telemetry;

import com.ig.sre.resilience.core.context.RequestContext;
import com.ig.sre.resilience.core.circuit.CircuitState;

public interface Telemetry {

    TelemetryContext start(RequestContext context);

    void recordRequest(String dependencyKey, String operationName, String outcome);

    void recordLatency(String dependencyKey, String operationName, long latencyMs);

    void recordRetry(String dependencyKey, String operationName);

    void recordCircuitState(String dependencyKey, CircuitState state);

    void recordRateLimited(String dependencyKey, String operationName, String clientKey);

    interface TelemetryContext extends AutoCloseable {

        void attribute(String key, String value);

        @Override
        void close();
    }
}
