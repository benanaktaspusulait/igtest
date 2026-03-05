package com.ig.sre.tubestatus.config;

import com.ig.sre.tubestatus.common.AppConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = AppConstants.PropertyPrefixes.TFL)
@Validated
public record TflProperties(
        @NotBlank @DefaultValue(AppConstants.Tfl.BASE_URL) String baseUrl,
        @NotBlank @DefaultValue(AppConstants.Tfl.LINE_STATUS_PATH) String lineStatusPath,
        @NotBlank @DefaultValue(AppConstants.Tfl.LINE_STATUS_RANGE_PATH) String lineStatusRangePath,
        @NotBlank @DefaultValue(AppConstants.Tfl.ALL_TUBE_STATUSES_PATH) String allTubeStatusesPath,
        @Positive @DefaultValue("1000") int connectTimeoutMillis,
        @Positive @DefaultValue("2000") int readTimeoutMillis,
        @Positive @DefaultValue("200") int maxInFlight,
        String appId,
        String appKey,
        @Valid @DefaultValue SyntheticFaultProperties syntheticFault
) {
    public TflProperties {
        syntheticFault = syntheticFault == null ? SyntheticFaultProperties.DISABLED : syntheticFault;
    }
}
