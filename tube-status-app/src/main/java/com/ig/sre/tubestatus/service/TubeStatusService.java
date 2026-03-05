package com.ig.sre.tubestatus.service;

import com.github.benmanes.caffeine.cache.Cache;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import com.ig.sre.tubestatus.api.model.LineStatusResponse;
import com.ig.sre.tubestatus.api.model.UnplannedDisruptionsResponse;
import com.ig.sre.tubestatus.client.tfl.TflClient;
import com.ig.sre.tubestatus.client.tfl.model.TflLine;
import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.exception.TubeLineNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class TubeStatusService {

    private final TflClient tflClient;
    private final TubeStatusMapper mapper;
    private final ResilientEndpointExecutor resilientEndpointExecutor;
    private final TubeStatusCacheSupport tubeStatusCacheSupport;
    private final TubeStatusQualityMetrics tubeStatusQualityMetrics;
    private final TubeStatusErrorMapper tubeStatusErrorMapper;
    private final Cache<String, LineStatusResponse> lineStatusCache;
    private final Cache<String, UnplannedDisruptionsResponse> unplannedDisruptionCache;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Service stores framework-managed collaborators injected by Spring."
    )
    public TubeStatusService(
            TflClient tflClient,
            TubeStatusMapper mapper,
            ResilientEndpointExecutor resilientEndpointExecutor,
            TubeStatusCacheSupport tubeStatusCacheSupport,
            TubeStatusQualityMetrics tubeStatusQualityMetrics,
            TubeStatusErrorMapper tubeStatusErrorMapper,
            @Qualifier(AppConstants.CacheNames.LINE_STATUS_CACHE_BEAN) Cache<String, LineStatusResponse> lineStatusCache,
            @Qualifier(AppConstants.CacheNames.UNPLANNED_DISRUPTION_CACHE_BEAN) Cache<String, UnplannedDisruptionsResponse> unplannedDisruptionCache
    ) {
        this.tflClient = tflClient;
        this.mapper = mapper;
        this.resilientEndpointExecutor = resilientEndpointExecutor;
        this.tubeStatusCacheSupport = tubeStatusCacheSupport;
        this.tubeStatusQualityMetrics = tubeStatusQualityMetrics;
        this.tubeStatusErrorMapper = tubeStatusErrorMapper;
        this.lineStatusCache = lineStatusCache;
        this.unplannedDisruptionCache = unplannedDisruptionCache;
    }

    public LineStatusResponse getLineStatus(
            String lineId,
            LocalDate startDate,
            LocalDate endDate,
            String clientKey
    ) {
        String endpoint = AppConstants.Metrics.ENDPOINT_LINE_STATUS;
        String cacheKey = tubeStatusCacheSupport.buildLineStatusCacheKey(lineId, startDate, endDate);
        return resilientEndpointExecutor.execute(
                endpoint,
                () -> {
                    List<TflLine> response = startDate == null
                            ? tflClient.getLineStatus(lineId, clientKey)
                            : tflClient.getLineStatusInRange(lineId, startDate, endDate, clientKey);

                    TflLine line = response.stream()
                            .findFirst()
                            .orElseThrow(() -> new TubeLineNotFoundException(lineId));

                    LineStatusResponse liveResponse = mapper.toLineStatusResponse(line, false, Instant.now());
                    lineStatusCache.put(cacheKey, liveResponse);
                    return liveResponse;
                },
                () -> lineStatusCache.getIfPresent(cacheKey),
                tubeStatusCacheSupport::markLineStatusStale,
                tubeStatusQualityMetrics::recordLineStatusQualityIfPresent,
                ex -> tubeStatusErrorMapper.toLineStatusClientError(ex, lineId),
                new ResilientEndpointExecutor.FallbackMessages(
                        AppConstants.Messages.LINE_STATUS_CACHE_MISS_UPSTREAM,
                        AppConstants.Messages.LINE_STATUS_CACHE_MISS_SATURATED
                )
        );
    }

    public UnplannedDisruptionsResponse getUnplannedDisruptions(String clientKey) {
        String endpoint = AppConstants.Metrics.ENDPOINT_UNPLANNED_DISRUPTIONS;
        return resilientEndpointExecutor.execute(
                endpoint,
                () -> {
                    List<TflLine> response = tflClient.getAllTubeStatuses(clientKey);
                    UnplannedDisruptionsResponse liveResponse =
                            mapper.toUnplannedDisruptionsResponse(response, false, Instant.now());
                    unplannedDisruptionCache.put(AppConstants.CacheNames.UNPLANNED_DISRUPTION_CACHE_KEY, liveResponse);
                    return liveResponse;
                },
                () -> unplannedDisruptionCache.getIfPresent(AppConstants.CacheNames.UNPLANNED_DISRUPTION_CACHE_KEY),
                tubeStatusCacheSupport::markUnplannedStale,
                tubeStatusQualityMetrics::recordUnplannedDisruptionsQualityIfPresent,
                tubeStatusErrorMapper::toDisruptionsClientError,
                new ResilientEndpointExecutor.FallbackMessages(
                        AppConstants.Messages.DISRUPTION_CACHE_MISS_UPSTREAM,
                        AppConstants.Messages.DISRUPTION_CACHE_MISS_SATURATED
                )
        );
    }
}
