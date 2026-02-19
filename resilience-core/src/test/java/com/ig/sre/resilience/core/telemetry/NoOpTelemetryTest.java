package com.ig.sre.resilience.core.telemetry;

import com.ig.sre.resilience.core.circuit.CircuitState;
import com.ig.sre.resilience.core.context.RequestContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class NoOpTelemetryTest {

    @Test
    void noOpTelemetryMethodsDoNotThrow() {
        NoOpTelemetry telemetry = new NoOpTelemetry();

        assertThatCode(() -> {
            Telemetry.TelemetryContext context = telemetry.start(new RequestContext("tfl", "lineStatus", "ip-1"));
            context.attribute("retry.count", "1");
            context.close();

            telemetry.recordRequest("tfl", "lineStatus", "success");
            telemetry.recordLatency("tfl", "lineStatus", 12);
            telemetry.recordRetry("tfl", "lineStatus");
            telemetry.recordCircuitState("tfl", CircuitState.CLOSED);
            telemetry.recordRateLimited("tfl", "lineStatus", "ip-1");
        }).doesNotThrowAnyException();
    }
}
