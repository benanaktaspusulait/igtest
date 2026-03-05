package com.ig.sre.tubestatus.service;

import com.github.benmanes.caffeine.cache.Cache;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import com.ig.sre.tubestatus.api.model.LineStatusResponse;
import com.ig.sre.tubestatus.api.model.UnplannedDisruptionsResponse;
import com.ig.sre.tubestatus.client.tfl.TflClient;
import com.ig.sre.tubestatus.client.tfl.model.TflLine;
import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.config.SliProperties;
import com.ig.sre.tubestatus.exception.BadRequestException;
import com.ig.sre.tubestatus.exception.DependencySaturatedException;
import com.ig.sre.tubestatus.exception.TooManyRequestsException;
import com.ig.sre.tubestatus.exception.TubeLineNotFoundException;
import com.ig.sre.tubestatus.exception.UpstreamUnavailableException;
import com.ig.sre.resilience.core.circuit.CircuitBreakerOpenException;
import com.ig.sre.resilience.core.error.ErrorCategory;
import com.ig.sre.resilience.core.error.UpstreamException;
import com.ig.sre.resilience.core.ratelimit.RateLimitExceededException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class TubeStatusService {

    private final TflClient tflClient;
    private final TubeStatusMapper mapper;
    private final MeterRegistry meterRegistry;
    private final Cache<String, LineStatusResponse> lineStatusCache;
    private final Cache<String, UnplannedDisruptionsResponse> unplannedDisruptionCache;
    private final SliProperties sliProperties;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Service stores framework-managed collaborators injected by Spring."
    )
    public TubeStatusService(
            TflClient tflClient,
            TubeStatusMapper mapper,
            MeterRegistry meterRegistry,
            @Qualifier(AppConstants.CacheNames.LINE_STATUS_CACHE_BEAN) Cache<String, LineStatusResponse> lineStatusCache,
            @Qualifier(AppConstants.CacheNames.UNPLANNED_DISRUPTION_CACHE_BEAN) Cache<String, UnplannedDisruptionsResponse> unplannedDisruptionCache,
            SliProperties sliProperties
    ) {
        this.tflClient = tflClient;
        this.mapper = mapper;
        this.meterRegistry = meterRegistry;
        this.lineStatusCache = lineStatusCache;
        this.unplannedDisruptionCache = unplannedDisruptionCache;
        this.sliProperties = sliProperties;
    }

    public LineStatusResponse getLineStatus(
            String lineId,
            LocalDate startDate,
            LocalDate endDate,
            String clientKey
    ) {
        String cacheKey = buildLineStatusCacheKey(lineId, startDate, endDate);
        Timer.Sample timer = Timer.start(meterRegistry);

        try {
            List<TflLine> response = startDate == null
                    ? tflClient.getLineStatus(lineId, clientKey)
                    : tflClient.getLineStatusInRange(lineId, startDate, endDate, clientKey);

            TflLine line = response.stream()
                    .findFirst()
                    .orElseThrow(() -> new TubeLineNotFoundException(lineId));

            LineStatusResponse liveResponse = mapper.toLineStatusResponse(line, false, Instant.now());
            lineStatusCache.put(cacheKey, liveResponse);
            incrementRequestCounter(AppConstants.Metrics.ENDPOINT_LINE_STATUS, AppConstants.Metrics.OUTCOME_LIVE);
            recordLineStatusQuality(liveResponse);
            return liveResponse;
        } catch (RateLimitExceededException ex) {
            incrementRequestCounter(AppConstants.Metrics.ENDPOINT_LINE_STATUS, AppConstants.Metrics.OUTCOME_RATE_LIMITED);
            throw new TooManyRequestsException(ex.getRetryAfterSeconds());
        } catch (DependencySaturatedException ex) {
            LineStatusResponse cached = lineStatusCache.getIfPresent(cacheKey);
            if (cached != null) {
                incrementRequestCounter(AppConstants.Metrics.ENDPOINT_LINE_STATUS, AppConstants.Metrics.OUTCOME_STALE);
                LineStatusResponse staleResponse = markLineStatusStale(cached);
                recordLineStatusQuality(staleResponse);
                return staleResponse;
            }

            incrementRequestCounter(AppConstants.Metrics.ENDPOINT_LINE_STATUS, AppConstants.Metrics.OUTCOME_UNAVAILABLE);
            throw new UpstreamUnavailableException(AppConstants.Messages.LINE_STATUS_CACHE_MISS_SATURATED, ex);
        } catch (CircuitBreakerOpenException ex) {
            LineStatusResponse cached = lineStatusCache.getIfPresent(cacheKey);
            if (cached != null) {
                incrementRequestCounter(AppConstants.Metrics.ENDPOINT_LINE_STATUS, AppConstants.Metrics.OUTCOME_STALE);
                LineStatusResponse staleResponse = markLineStatusStale(cached);
                recordLineStatusQuality(staleResponse);
                return staleResponse;
            }

            incrementRequestCounter(AppConstants.Metrics.ENDPOINT_LINE_STATUS, AppConstants.Metrics.OUTCOME_UNAVAILABLE);
            throw new UpstreamUnavailableException(
                    withRetryAfterMessage(AppConstants.Messages.LINE_STATUS_CACHE_MISS_UPSTREAM, ex.getRetryAfterSeconds()),
                    ex
            );
        } catch (UpstreamException ex) {
            if (isClientError(ex)) {
                incrementRequestCounter(AppConstants.Metrics.ENDPOINT_LINE_STATUS, AppConstants.Metrics.OUTCOME_CLIENT_ERROR);
                throw toLineStatusClientError(ex, lineId);
            }

            LineStatusResponse cached = lineStatusCache.getIfPresent(cacheKey);
            if (cached != null) {
                incrementRequestCounter(AppConstants.Metrics.ENDPOINT_LINE_STATUS, AppConstants.Metrics.OUTCOME_STALE);
                LineStatusResponse staleResponse = markLineStatusStale(cached);
                recordLineStatusQuality(staleResponse);
                return staleResponse;
            }

            incrementRequestCounter(AppConstants.Metrics.ENDPOINT_LINE_STATUS, AppConstants.Metrics.OUTCOME_UNAVAILABLE);
            throw new UpstreamUnavailableException(AppConstants.Messages.LINE_STATUS_CACHE_MISS_UPSTREAM, ex);
        } finally {
            timer.stop(
                    Timer.builder(AppConstants.Metrics.TUBE_STATUS_REQUEST_LATENCY)
                            .description(AppConstants.Metrics.REQUEST_LATENCY_DESCRIPTION)
                            .tag(AppConstants.Metrics.TAG_ENDPOINT, AppConstants.Metrics.ENDPOINT_LINE_STATUS)
                            .register(meterRegistry)
            );
        }
    }

    public UnplannedDisruptionsResponse getUnplannedDisruptions(String clientKey) {
        Timer.Sample timer = Timer.start(meterRegistry);

        try {
            List<TflLine> response = tflClient.getAllTubeStatuses(clientKey);
            UnplannedDisruptionsResponse liveResponse =
                    mapper.toUnplannedDisruptionsResponse(response, false, Instant.now());

            unplannedDisruptionCache.put(AppConstants.CacheNames.UNPLANNED_DISRUPTION_CACHE_KEY, liveResponse);
            incrementRequestCounter(AppConstants.Metrics.ENDPOINT_UNPLANNED_DISRUPTIONS, AppConstants.Metrics.OUTCOME_LIVE);
            recordUnplannedDisruptionsQuality(liveResponse);
            return liveResponse;
        } catch (RateLimitExceededException ex) {
            incrementRequestCounter(
                    AppConstants.Metrics.ENDPOINT_UNPLANNED_DISRUPTIONS,
                    AppConstants.Metrics.OUTCOME_RATE_LIMITED
            );
            throw new TooManyRequestsException(ex.getRetryAfterSeconds());
        } catch (DependencySaturatedException ex) {
            UnplannedDisruptionsResponse cached = unplannedDisruptionCache.getIfPresent(
                    AppConstants.CacheNames.UNPLANNED_DISRUPTION_CACHE_KEY
            );
            if (cached != null) {
                incrementRequestCounter(
                        AppConstants.Metrics.ENDPOINT_UNPLANNED_DISRUPTIONS,
                        AppConstants.Metrics.OUTCOME_STALE
                );
                UnplannedDisruptionsResponse staleResponse = markUnplannedStale(cached);
                recordUnplannedDisruptionsQuality(staleResponse);
                return staleResponse;
            }

            incrementRequestCounter(
                    AppConstants.Metrics.ENDPOINT_UNPLANNED_DISRUPTIONS,
                    AppConstants.Metrics.OUTCOME_UNAVAILABLE
            );
            throw new UpstreamUnavailableException(AppConstants.Messages.DISRUPTION_CACHE_MISS_SATURATED, ex);
        } catch (CircuitBreakerOpenException ex) {
            UnplannedDisruptionsResponse cached = unplannedDisruptionCache.getIfPresent(
                    AppConstants.CacheNames.UNPLANNED_DISRUPTION_CACHE_KEY
            );
            if (cached != null) {
                incrementRequestCounter(
                        AppConstants.Metrics.ENDPOINT_UNPLANNED_DISRUPTIONS,
                        AppConstants.Metrics.OUTCOME_STALE
                );
                UnplannedDisruptionsResponse staleResponse = markUnplannedStale(cached);
                recordUnplannedDisruptionsQuality(staleResponse);
                return staleResponse;
            }

            incrementRequestCounter(
                    AppConstants.Metrics.ENDPOINT_UNPLANNED_DISRUPTIONS,
                    AppConstants.Metrics.OUTCOME_UNAVAILABLE
            );
            throw new UpstreamUnavailableException(
                    withRetryAfterMessage(AppConstants.Messages.DISRUPTION_CACHE_MISS_UPSTREAM, ex.getRetryAfterSeconds()),
                    ex
            );
        } catch (UpstreamException ex) {
            if (isClientError(ex)) {
                incrementRequestCounter(
                        AppConstants.Metrics.ENDPOINT_UNPLANNED_DISRUPTIONS,
                        AppConstants.Metrics.OUTCOME_CLIENT_ERROR
                );
                throw new BadRequestException(AppConstants.Messages.INVALID_DISRUPTION_REQUEST_PREFIX + ex.getMessage());
            }

            UnplannedDisruptionsResponse cached = unplannedDisruptionCache.getIfPresent(
                    AppConstants.CacheNames.UNPLANNED_DISRUPTION_CACHE_KEY
            );
            if (cached != null) {
                incrementRequestCounter(
                        AppConstants.Metrics.ENDPOINT_UNPLANNED_DISRUPTIONS,
                        AppConstants.Metrics.OUTCOME_STALE
                );
                UnplannedDisruptionsResponse staleResponse = markUnplannedStale(cached);
                recordUnplannedDisruptionsQuality(staleResponse);
                return staleResponse;
            }

            incrementRequestCounter(
                    AppConstants.Metrics.ENDPOINT_UNPLANNED_DISRUPTIONS,
                    AppConstants.Metrics.OUTCOME_UNAVAILABLE
            );
            throw new UpstreamUnavailableException(AppConstants.Messages.DISRUPTION_CACHE_MISS_UPSTREAM, ex);
        } finally {
            timer.stop(
                    Timer.builder(AppConstants.Metrics.TUBE_STATUS_REQUEST_LATENCY)
                            .description(AppConstants.Metrics.REQUEST_LATENCY_DESCRIPTION)
                            .tag(AppConstants.Metrics.TAG_ENDPOINT, AppConstants.Metrics.ENDPOINT_UNPLANNED_DISRUPTIONS)
                            .register(meterRegistry)
            );
        }
    }

    private RuntimeException toLineStatusClientError(Throwable throwable, String lineId) {
        if (throwable instanceof UpstreamException upstreamException
                && upstreamException.getStatusCode() != null
                && upstreamException.getStatusCode() == 404) {
            return new TubeLineNotFoundException(lineId);
        }
        return new BadRequestException(AppConstants.Messages.INVALID_LINE_STATUS_REQUEST_PREFIX + throwable.getMessage());
    }

    private String withRetryAfterMessage(String baseMessage, long retryAfterSeconds) {
        return baseMessage + AppConstants.Messages.RETRY_AFTER_SECONDS_SUFFIX_TEMPLATE.formatted(retryAfterSeconds);
    }

    private boolean isClientError(Throwable throwable) {
        return throwable instanceof UpstreamException upstreamException
                && upstreamException.getCategory() == ErrorCategory.CLIENT_ERROR;
    }

    private String buildLineStatusCacheKey(String lineId, LocalDate startDate, LocalDate endDate) {
        return AppConstants.Formats.LINE_STATUS_CACHE_KEY_FORMAT.formatted(
                lineId.toLowerCase(Locale.ROOT),
                startDate == null ? AppConstants.CacheNames.CURRENT_KEY_PART : startDate,
                endDate == null ? AppConstants.CacheNames.CURRENT_KEY_PART : endDate
        );
    }

    private LineStatusResponse markLineStatusStale(LineStatusResponse cached) {
        return new LineStatusResponse(
                cached.lineId(),
                cached.lineName(),
                cached.statuses(),
                true,
                cached.fetchedAt()
        );
    }

    private UnplannedDisruptionsResponse markUnplannedStale(UnplannedDisruptionsResponse cached) {
        return new UnplannedDisruptionsResponse(
                cached.lines(),
                true,
                cached.fetchedAt()
        );
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

    private void recordLineStatusQuality(LineStatusResponse response) {
        recordFreshness(AppConstants.Metrics.ENDPOINT_LINE_STATUS, response.fetchedAt());
        recordCorrectness(AppConstants.Metrics.ENDPOINT_LINE_STATUS, isLineStatusResponseValid(response));
    }

    private void recordUnplannedDisruptionsQuality(UnplannedDisruptionsResponse response) {
        recordFreshness(AppConstants.Metrics.ENDPOINT_UNPLANNED_DISRUPTIONS, response.fetchedAt());
        recordCorrectness(
                AppConstants.Metrics.ENDPOINT_UNPLANNED_DISRUPTIONS,
                isUnplannedDisruptionsResponseValid(response)
        );
    }

    private void recordFreshness(String endpoint, Instant fetchedAt) {
        boolean fresh = isFresh(fetchedAt);
        meterRegistry.counter(
                AppConstants.Metrics.TUBE_STATUS_FRESHNESS_TOTAL,
                AppConstants.Metrics.TAG_ENDPOINT,
                endpoint,
                AppConstants.Metrics.TAG_RESULT,
                fresh ? AppConstants.Metrics.RESULT_FRESH : AppConstants.Metrics.RESULT_STALE
        ).increment();
    }

    private boolean isFresh(Instant fetchedAt) {
        if (fetchedAt == null) {
            return false;
        }

        Duration dataAge = Duration.between(fetchedAt, Instant.now());
        if (dataAge.isNegative()) {
            return true;
        }
        return dataAge.compareTo(sliProperties.freshnessThreshold()) <= 0;
    }

    private void recordCorrectness(String endpoint, boolean valid) {
        meterRegistry.counter(
                AppConstants.Metrics.TUBE_STATUS_CORRECTNESS_TOTAL,
                AppConstants.Metrics.TAG_ENDPOINT,
                endpoint,
                AppConstants.Metrics.TAG_RESULT,
                valid ? AppConstants.Metrics.RESULT_VALID : AppConstants.Metrics.RESULT_INVALID
        ).increment();
    }

    private boolean isLineStatusResponseValid(LineStatusResponse response) {
        return response != null
                && StringUtils.hasText(response.lineId())
                && StringUtils.hasText(response.lineName())
                && response.statuses() != null
                && !response.statuses().isEmpty();
    }

    private boolean isUnplannedDisruptionsResponseValid(UnplannedDisruptionsResponse response) {
        if (response == null || response.lines() == null) {
            return false;
        }

        return response.lines().stream().allMatch(line ->
                StringUtils.hasText(line.lineId())
                        && StringUtils.hasText(line.lineName())
                        && line.statuses() != null
                        && !line.statuses().isEmpty()
        );
    }
}
