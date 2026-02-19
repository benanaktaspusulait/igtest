package com.ig.sre.tubestatus.api.model;

import java.util.List;

public record UnplannedLineDisruption(
        String lineId,
        String lineName,
        List<StatusEntry> statuses
) {
    public UnplannedLineDisruption {
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
    }
}
