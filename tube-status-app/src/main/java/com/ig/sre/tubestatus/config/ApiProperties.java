package com.ig.sre.tubestatus.config;

import com.ig.sre.tubestatus.common.AppConstants;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

@ConfigurationProperties(prefix = AppConstants.PropertyPrefixes.APP_API)
@Validated
public class ApiProperties {

    @NotBlank
    private String basePath = AppConstants.Api.DEFAULT_BASE_PATH;

    @NotBlank
    private String lineStatusPath = AppConstants.Api.DEFAULT_LINE_STATUS_PATH;

    @NotBlank
    private String unplannedDisruptionsPath = AppConstants.Api.DEFAULT_UNPLANNED_DISRUPTIONS_PATH;

    @NotBlank
    private String defaultVersion = AppConstants.Api.DEFAULT_API_VERSION;

    @NotBlank
    private String versionRange = AppConstants.Api.DEFAULT_API_VERSION_RANGE;

    @NotBlank
    private String versionHeader = AppConstants.Api.DEFAULT_API_VERSION_HEADER;

    private boolean trustForwardHeaders;

    private List<String> trustedProxyIps = List.of();

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getLineStatusPath() {
        return lineStatusPath;
    }

    public void setLineStatusPath(String lineStatusPath) {
        this.lineStatusPath = lineStatusPath;
    }

    public String getUnplannedDisruptionsPath() {
        return unplannedDisruptionsPath;
    }

    public void setUnplannedDisruptionsPath(String unplannedDisruptionsPath) {
        this.unplannedDisruptionsPath = unplannedDisruptionsPath;
    }

    public String getDefaultVersion() {
        return defaultVersion;
    }

    public void setDefaultVersion(String defaultVersion) {
        this.defaultVersion = defaultVersion;
    }

    public String getVersionRange() {
        return versionRange;
    }

    public void setVersionRange(String versionRange) {
        this.versionRange = versionRange;
    }

    public String getVersionHeader() {
        return versionHeader;
    }

    public void setVersionHeader(String versionHeader) {
        this.versionHeader = versionHeader;
    }

    public boolean isTrustForwardHeaders() {
        return trustForwardHeaders;
    }

    public void setTrustForwardHeaders(boolean trustForwardHeaders) {
        this.trustForwardHeaders = trustForwardHeaders;
    }

    public List<String> getTrustedProxyIps() {
        return List.copyOf(trustedProxyIps);
    }

    public void setTrustedProxyIps(List<String> trustedProxyIps) {
        if (trustedProxyIps == null) {
            this.trustedProxyIps = List.of();
            return;
        }
        this.trustedProxyIps = List.copyOf(trustedProxyIps.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(ip -> !ip.isBlank())
                .toList());
    }

}
