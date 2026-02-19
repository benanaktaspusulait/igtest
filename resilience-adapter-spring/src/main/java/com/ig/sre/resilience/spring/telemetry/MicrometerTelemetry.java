package com.ig.sre.resilience.spring.telemetry;

import com.ig.sre.resilience.spring.config.SpringAdapterConstants;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import com.ig.sre.resilience.core.context.RequestContext;
import com.ig.sre.resilience.core.circuit.CircuitState;
import com.ig.sre.resilience.core.telemetry.Telemetry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MicrometerTelemetry implements Telemetry {

    private final MeterRegistry meterRegistry;
    private final Tracer tracer;
    private final Map<String, Map<CircuitState, AtomicInteger>> circuitStateGauges = new ConcurrentHashMap<>();

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "MeterRegistry is a framework-managed singleton dependency for telemetry wiring."
    )
    public MicrometerTelemetry(MeterRegistry meterRegistry, Tracer tracer) {
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
    }

    @Override
    public TelemetryContext start(RequestContext context) {
        if (tracer == null) {
            return NoOpTelemetryContext.INSTANCE;
        }

        Span span = tracer.nextSpan()
                .name(context.dependencyKey() + SpringAdapterConstants.Tracing.SPAN_SUFFIX_REQUEST)
                .tag(SpringAdapterConstants.Metrics.TAG_DEPENDENCY, context.dependencyKey())
                .tag(SpringAdapterConstants.Metrics.TAG_OPERATION, context.operationName())
                .tag(SpringAdapterConstants.Tracing.TAG_CLIENT, context.clientKeyOrAnonymous())
                .start();

        Tracer.SpanInScope scope = tracer.withSpan(span);
        return new TracingTelemetryContext(span, scope);
    }

    @Override
    public void recordRequest(String dependencyKey, String operationName, String outcome) {
        meterRegistry.counter(
                SpringAdapterConstants.Metrics.REQUESTS_TOTAL,
                Tags.of(
                        SpringAdapterConstants.Metrics.TAG_DEPENDENCY,
                        dependencyKey,
                        SpringAdapterConstants.Metrics.TAG_OPERATION,
                        operationName,
                        SpringAdapterConstants.Metrics.TAG_OUTCOME,
                        outcome
                )
        ).increment();
    }

    @Override
    public void recordLatency(String dependencyKey, String operationName, long latencyMs) {
        meterRegistry.summary(
                SpringAdapterConstants.Metrics.LATENCY_MS,
                Tags.of(
                        SpringAdapterConstants.Metrics.TAG_DEPENDENCY,
                        dependencyKey,
                        SpringAdapterConstants.Metrics.TAG_OPERATION,
                        operationName
                )
        ).record(latencyMs);
    }

    @Override
    public void recordRetry(String dependencyKey, String operationName) {
        meterRegistry.counter(
                SpringAdapterConstants.Metrics.RETRIES_TOTAL,
                Tags.of(
                        SpringAdapterConstants.Metrics.TAG_DEPENDENCY,
                        dependencyKey,
                        SpringAdapterConstants.Metrics.TAG_OPERATION,
                        operationName
                )
        ).increment();
    }

    @Override
    public void recordCircuitState(String dependencyKey, CircuitState state) {
        Map<CircuitState, AtomicInteger> gauges = circuitStateGauges.computeIfAbsent(
                dependencyKey,
                this::registerCircuitStateGauges
        );

        gauges.forEach((candidate, gauge) -> gauge.set(candidate == state ? 1 : 0));
    }

    @Override
    public void recordRateLimited(String dependencyKey, String operationName, String clientKey) {
        String clientType = clientKey == null || clientKey.isBlank()
                ? SpringAdapterConstants.Metrics.CLIENT_ANONYMOUS
                : SpringAdapterConstants.Metrics.CLIENT_IDENTIFIED;
        meterRegistry.counter(
                SpringAdapterConstants.Metrics.RATE_LIMITED_TOTAL,
                Tags.of(
                        SpringAdapterConstants.Metrics.TAG_DEPENDENCY,
                        dependencyKey,
                        SpringAdapterConstants.Metrics.TAG_OPERATION,
                        operationName,
                        SpringAdapterConstants.Metrics.TAG_CLIENT_TYPE,
                        clientType
                )
        ).increment();
    }

    private Map<CircuitState, AtomicInteger> registerCircuitStateGauges(String dependencyKey) {
        Map<CircuitState, AtomicInteger> gauges = new EnumMap<>(CircuitState.class);

        for (CircuitState state : CircuitState.values()) {
            AtomicInteger gaugeValue = new AtomicInteger(state == CircuitState.CLOSED ? 1 : 0);
            gauges.put(state, gaugeValue);
            meterRegistry.gauge(
                    SpringAdapterConstants.Metrics.CIRCUIT_STATE,
                    Tags.of(
                            SpringAdapterConstants.Metrics.TAG_DEPENDENCY,
                            dependencyKey,
                            SpringAdapterConstants.Metrics.TAG_STATE,
                            state.name().toLowerCase(Locale.ROOT)
                    ),
                    gaugeValue
            );
        }

        return gauges;
    }

    private record TracingTelemetryContext(Span span, Tracer.SpanInScope scope) implements TelemetryContext {
        @Override
        public void attribute(String key, String value) {
            span.tag(key, value);
        }

        @Override
        public void close() {
            scope.close();
            span.end();
        }
    }
}
