package com.ig.sre.tubestatus.client.tfl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TflLine(
        String id,
        String name,
        String modeName,
        List<TflDisruption> disruptions,
        List<TflLineStatus> lineStatuses
) {
    public TflLine {
        disruptions = disruptions == null ? List.of() : List.copyOf(disruptions);
        lineStatuses = lineStatuses == null ? List.of() : List.copyOf(lineStatuses);
    }
}
