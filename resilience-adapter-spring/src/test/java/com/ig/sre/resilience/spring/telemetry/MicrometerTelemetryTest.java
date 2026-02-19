package com.ig.sre.resilience.spring.telemetry;

import com.ig.sre.resilience.core.circuit.CircuitState;
import com.ig.sre.resilience.core.context.RequestContext;
import com.ig.sre.resilience.core.telemetry.Telemetry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MicrometerTelemetryTest {

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @Mock
    private Tracer.SpanInScope spanInScope;

    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void recordsMetricsWithExpectedTags() {
        MicrometerTelemetry telemetry = new MicrometerTelemetry(meterRegistry, null);

        telemetry.recordRequest("tfl", "line_status", "success");
        telemetry.recordLatency("tfl", "line_status", 125L);
        telemetry.recordRetry("tfl", "line_status");
        telemetry.recordCircuitState("tfl", CircuitState.OPEN);
        telemetry.recordRateLimited("tfl", "line_status", "");

        assertThat(
                Objects.requireNonNull(meterRegistry.find("requests_total")
                                .tags("dependency", "tfl", "operation", "line_status", "outcome", "success")
                                .counter())
                        .count()
        ).isEqualTo(1.0d);
        assertThat(
                Objects.requireNonNull(meterRegistry.find("retries_total")
                                .tags("dependency", "tfl", "operation", "line_status")
                                .counter())
                        .count()
        ).isEqualTo(1.0d);
        assertThat(
                Objects.requireNonNull(meterRegistry.find("rate_limited_total")
                                .tags("dependency", "tfl", "operation", "line_status", "clientType", "anonymous")
                                .counter())
                        .count()
        ).isEqualTo(1.0d);
        assertThat(
                Objects.requireNonNull(meterRegistry.find("circuit_state")
                                .tags("dependency", "tfl", "state", "open")
                                .gauge())
                        .value()
        ).isEqualTo(1.0d);
    }

    @Test
    void recordsTracingAttributes() {
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.tag(anyString(), anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(spanInScope);
        MicrometerTelemetry telemetry = new MicrometerTelemetry(meterRegistry, tracer);

        Telemetry.TelemetryContext context = telemetry.start(new RequestContext("tfl", "line_status", "ip-1"));
        context.attribute("retry.count", "2");
        context.close();

        verify(span).name("tfl.request");
        verify(span).tag("dependency", "tfl");
        verify(span).tag("operation", "line_status");
        verify(span).tag("client", "ip-1");
        verify(span).tag("retry.count", "2");
        verify(spanInScope).close();
        verify(span).end();
    }
}
