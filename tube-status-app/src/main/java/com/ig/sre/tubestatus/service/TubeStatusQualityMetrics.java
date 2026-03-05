package com.ig.sre.tubestatus.service;

import com.ig.sre.tubestatus.api.model.LineStatusResponse;
import com.ig.sre.tubestatus.api.model.UnplannedDisruptionsResponse;
import com.ig.sre.tubestatus.common.AppConstants;
import com.ig.sre.tubestatus.config.SliProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;

@Component
public class TubeStatusQualityMetrics {

    private final MeterRegistry meterRegistry;
    private final SliProperties sliProperties;

    public TubeStatusQualityMetrics(MeterRegistry meterRegistry, SliProperties sliProperties) {
        this.meterRegistry = meterRegistry;
        this.sliProperties = sliProperties;
    }

    public void recordLineStatusQualityIfPresent(LineStatusResponse response) {
        if (response != null) {
            recordLineStatusQuality(response);
        }
    }

    public void recordUnplannedDisruptionsQualityIfPresent(UnplannedDisruptionsResponse response) {
        if (response != null) {
            recordUnplannedDisruptionsQuality(response);
        }
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
