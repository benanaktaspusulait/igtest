package com.ig.sre.resilience.core.telemetry;

import com.ig.sre.resilience.core.context.RequestContext;
import com.ig.sre.resilience.core.circuit.CircuitState;

public final class NoOpTelemetry implements Telemetry {

    private static final TelemetryContext NO_OP_CONTEXT = new TelemetryContext() {
        @Override
        public void attribute(String key, String value) {
            // no-op
        }

        @Override
        public void close() {
            // no-op
        }
    };

    @Override
    public TelemetryContext start(RequestContext context) {
        return NO_OP_CONTEXT;
    }

    @Override
    public void recordRequest(String dependencyKey, String operationName, String outcome) {
        // no-op
    }

    @Override
    public void recordLatency(String dependencyKey, String operationName, long latencyMs) {
        // no-op
    }

    @Override
    public void recordRetry(String dependencyKey, String operationName) {
        // no-op
    }

    @Override
    public void recordCircuitState(String dependencyKey, CircuitState state) {
        // no-op
    }

    @Override
    public void recordRateLimited(String dependencyKey, String operationName, String clientKey) {
        // no-op
    }
}
