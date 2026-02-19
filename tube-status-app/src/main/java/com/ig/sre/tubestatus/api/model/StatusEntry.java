package com.ig.sre.tubestatus.api.model;

import java.time.OffsetDateTime;

public record StatusEntry(
        String status,
        String reason,
        boolean planned,
        OffsetDateTime from,
        OffsetDateTime to
) {
}
