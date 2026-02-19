package com.ig.sre.tubestatus.client.tfl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TflLineStatus(
        Integer statusSeverity,
        String statusSeverityDescription,
        String reason,
        TflDisruption disruption,
        List<TflValidityPeriod> validityPeriods
) {
    public TflLineStatus {
        validityPeriods = validityPeriods == null ? List.of() : List.copyOf(validityPeriods);
    }
}
