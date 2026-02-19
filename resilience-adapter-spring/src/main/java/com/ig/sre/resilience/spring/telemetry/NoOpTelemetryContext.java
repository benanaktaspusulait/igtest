package com.ig.sre.resilience.spring.telemetry;

import com.ig.sre.resilience.core.telemetry.Telemetry;

public enum NoOpTelemetryContext implements Telemetry.TelemetryContext {
    INSTANCE;

    @Override
    public void attribute(String key, String value) {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }
}
