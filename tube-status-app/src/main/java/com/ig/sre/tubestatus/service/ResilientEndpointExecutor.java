package com.ig.sre.tubestatus.service;

import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.exception.DependencySaturatedException;
import com.ig.sre.tubestatus.exception.TooManyRequestsException;
import com.ig.sre.tubestatus.exception.UpstreamUnavailableException;
import com.ig.sre.resilience.core.circuit.CircuitBreakerOpenException;
import com.ig.sre.resilience.core.error.ErrorCategory;
import com.ig.sre.resilience.core.error.UpstreamException;
import com.ig.sre.resilience.core.ratelimit.RateLimitExceededException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@Component
public class ResilientEndpointExecutor {

    private final MeterRegistry meterRegistry;

    public ResilientEndpointExecutor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <T> T execute(
            String endpoint,
            Supplier<T> liveSupplier,
            Supplier<T> cacheSupplier,
            UnaryOperator<T> staleMapper,
            Consumer<T> qualityRecorder,
            Function<UpstreamException, RuntimeException> clientErrorMapper,
            FallbackMessages fallbackMessages
    ) {
        Timer.Sample timer = Timer.start(meterRegistry);
        try {
            T liveResponse = liveSupplier.get();
            incrementRequestCounter(endpoint, AppConstants.Metrics.OUTCOME_LIVE);
            qualityRecorder.accept(liveResponse);
            return liveResponse;
        } catch (RateLimitExceededException ex) {
            incrementRequestCounter(endpoint, AppConstants.Metrics.OUTCOME_RATE_LIMITED);
            throw new TooManyRequestsException(ex.getRetryAfterSeconds());
        } catch (DependencySaturatedException ex) {
            return fallbackFromCacheOrThrow(
                    endpoint,
                    cacheSupplier,
                    staleMapper,
                    qualityRecorder,
                    fallbackMessages.saturatedMessage(),
                    ex
            );
        } catch (CircuitBreakerOpenException ex) {
            return fallbackFromCacheOrThrow(
                    endpoint,
                    cacheSupplier,
                    staleMapper,
                    qualityRecorder,
                    withRetryAfterMessage(fallbackMessages.unavailableMessage(), ex.getRetryAfterSeconds()),
                    ex
            );
        } catch (UpstreamException ex) {
            if (isClientError(ex)) {
                incrementRequestCounter(endpoint, AppConstants.Metrics.OUTCOME_CLIENT_ERROR);
                throw clientErrorMapper.apply(ex);
            }

            return fallbackFromCacheOrThrow(
                    endpoint,
                    cacheSupplier,
                    staleMapper,
                    qualityRecorder,
                    fallbackMessages.unavailableMessage(),
                    ex
            );
        } finally {
            timer.stop(
                    Timer.builder(AppConstants.Metrics.TUBE_STATUS_REQUEST_LATENCY)
                            .description(AppConstants.Metrics.REQUEST_LATENCY_DESCRIPTION)
                            .tag(AppConstants.Metrics.TAG_ENDPOINT, endpoint)
                            .register(meterRegistry)
            );
        }
    }

    private <T> T fallbackFromCacheOrThrow(
            String endpoint,
            Supplier<T> cacheSupplier,
            UnaryOperator<T> staleMapper,
            Consumer<T> qualityRecorder,
            String unavailableMessage,
            Throwable cause
    ) {
        T cached = cacheSupplier.get();
        if (cached != null) {
            incrementRequestCounter(endpoint, AppConstants.Metrics.OUTCOME_STALE);
            T staleResponse = staleMapper.apply(cached);
            qualityRecorder.accept(staleResponse);
            return staleResponse;
        }

        incrementRequestCounter(endpoint, AppConstants.Metrics.OUTCOME_UNAVAILABLE);
        throw new UpstreamUnavailableException(unavailableMessage, cause);
    }

    private void incrementRequestCounter(String endpoint, String outcome) {
        meterRegistry.counter(
                AppConstants.Metrics.TUBE_STATUS_REQUESTS_TOTAL,
                AppConstants.Metrics.TAG_ENDPOINT,
                endpoint,
                AppConstants.Metrics.TAG_OUTCOME,
                outcome
        ).increment();
    }

    private String withRetryAfterMessage(String baseMessage, long retryAfterSeconds) {
        return baseMessage + AppConstants.Messages.RETRY_AFTER_SECONDS_SUFFIX_TEMPLATE.formatted(retryAfterSeconds);
    }

    private boolean isClientError(UpstreamException upstreamException) {
        return upstreamException.getCategory() == ErrorCategory.CLIENT_ERROR;
    }

    public record FallbackMessages(
            String unavailableMessage,
            String saturatedMessage
    ) {
    }
}
