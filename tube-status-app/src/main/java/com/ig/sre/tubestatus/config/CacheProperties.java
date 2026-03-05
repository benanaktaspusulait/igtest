package com.ig.sre.tubestatus.config;

import com.ig.sre.tubestatus.common.AppConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = AppConstants.PropertyPrefixes.CACHE)
@Validated
public record CacheProperties(
        @Valid @NotNull EntryProperties lineStatus,
        @Valid @NotNull EntryProperties unplannedDisruptions
) {
    private static final EntryProperties DEFAULT_LINE_STATUS = new EntryProperties(
            500L,
            Duration.ofMinutes(15)
    );

    private static final EntryProperties DEFAULT_UNPLANNED_DISRUPTIONS = new EntryProperties(
            10L,
            Duration.ofMinutes(5)
    );

    public CacheProperties {
        lineStatus = mergeWithDefaults(lineStatus, DEFAULT_LINE_STATUS);
        unplannedDisruptions = mergeWithDefaults(unplannedDisruptions, DEFAULT_UNPLANNED_DISRUPTIONS);
    }

    private static EntryProperties mergeWithDefaults(EntryProperties configured, EntryProperties defaults) {
        if (configured == null) {
            return defaults;
        }

        Long maximumSize = configured.maximumSize() == null ? defaults.maximumSize() : configured.maximumSize();
        Duration expireAfterWrite = configured.expireAfterWrite() == null
                ? defaults.expireAfterWrite()
                : configured.expireAfterWrite();

        return new EntryProperties(maximumSize, expireAfterWrite);
    }

    public record EntryProperties(
            @NotNull @Positive Long maximumSize,
            @NotNull Duration expireAfterWrite
    ) {
    }
}
