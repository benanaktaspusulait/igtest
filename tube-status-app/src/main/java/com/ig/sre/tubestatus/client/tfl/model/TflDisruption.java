package com.ig.sre.tubestatus.client.tfl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TflDisruption(
        String category,
        String categoryDescription,
        String description,
        Boolean isPlanned
) {
}
