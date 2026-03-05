package com.ig.sre.tubestatus.service;

import com.ig.sre.tubestatus.api.model.LineStatusResponse;
import com.ig.sre.tubestatus.api.model.UnplannedDisruptionsResponse;
import com.ig.sre.tubestatus.common.AppConstants;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Locale;

@Component
public class TubeStatusCacheSupport {

    public String buildLineStatusCacheKey(String lineId, LocalDate startDate, LocalDate endDate) {
        return AppConstants.Formats.LINE_STATUS_CACHE_KEY_FORMAT.formatted(
                lineId.toLowerCase(Locale.ROOT),
                startDate == null ? AppConstants.CacheNames.CURRENT_KEY_PART : startDate,
                endDate == null ? AppConstants.CacheNames.CURRENT_KEY_PART : endDate
        );
    }

    public LineStatusResponse markLineStatusStale(LineStatusResponse cached) {
        return new LineStatusResponse(
                cached.lineId(),
                cached.lineName(),
                cached.statuses(),
                true,
                cached.fetchedAt()
        );
    }

    public UnplannedDisruptionsResponse markUnplannedStale(UnplannedDisruptionsResponse cached) {
        return new UnplannedDisruptionsResponse(
                cached.lines(),
                true,
                cached.fetchedAt()
        );
    }
}
