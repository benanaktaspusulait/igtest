package com.ig.sre.tubestatus.config;

import com.ig.sre.tubestatus.common.AppConstants;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

@ConfigurationProperties(prefix = AppConstants.PropertyPrefixes.APP_API)
@Validated
public record ApiProperties(
        @NotBlank @DefaultValue(AppConstants.Api.DEFAULT_BASE_PATH) String basePath,
        @NotBlank @DefaultValue(AppConstants.Api.DEFAULT_LINE_STATUS_PATH) String lineStatusPath,
        @NotBlank @DefaultValue(AppConstants.Api.DEFAULT_UNPLANNED_DISRUPTIONS_PATH) String unplannedDisruptionsPath,
        @NotBlank @DefaultValue(AppConstants.Api.DEFAULT_API_VERSION) String defaultVersion,
        @NotBlank @DefaultValue(AppConstants.Api.DEFAULT_API_VERSION_RANGE) String versionRange,
        @NotBlank @DefaultValue(AppConstants.Api.DEFAULT_API_VERSION_HEADER) String versionHeader,
        @DefaultValue("false") boolean trustForwardHeaders,
        List<String> trustedProxyIps
) {
    public ApiProperties {
        trustedProxyIps = normalizeTrustedProxyIps(trustedProxyIps);
    }

    private static List<String> normalizeTrustedProxyIps(List<String> trustedProxyIps) {
        if (trustedProxyIps == null) {
            return List.of();
        }
        return List.copyOf(trustedProxyIps.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(ip -> !ip.isBlank())
                .toList());
    }
}
