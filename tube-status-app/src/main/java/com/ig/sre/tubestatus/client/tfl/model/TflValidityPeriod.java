package com.ig.sre.tubestatus.client.tfl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TflValidityPeriod(
        OffsetDateTime fromDate,
        OffsetDateTime toDate,
        Boolean isNow
) {
}
