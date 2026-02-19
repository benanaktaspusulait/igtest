package com.ig.sre.tubestatus.api.model;

import java.time.Instant;
import java.util.List;

public record LineStatusResponse(
        String lineId,
        String lineName,
        List<StatusEntry> statuses,
        boolean stale,
        Instant fetchedAt
) {
    public LineStatusResponse {
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
    }
}
