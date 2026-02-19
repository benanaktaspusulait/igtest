package com.ig.sre.tubestatus.api.model;

import java.time.Instant;
import java.util.List;

public record UnplannedDisruptionsResponse(
        List<UnplannedLineDisruption> lines,
        boolean stale,
        Instant fetchedAt
) {
    public UnplannedDisruptionsResponse {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
