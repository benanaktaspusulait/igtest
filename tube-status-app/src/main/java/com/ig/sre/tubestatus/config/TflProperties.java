package com.ig.sre.tubestatus.config;

import com.ig.sre.tubestatus.common.AppConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = AppConstants.PropertyPrefixes.TFL)
@Validated
public class TflProperties {

    @NotBlank
    private String baseUrl = AppConstants.Tfl.BASE_URL;

    @NotBlank
    private String lineStatusPath = AppConstants.Tfl.LINE_STATUS_PATH;

    @NotBlank
    private String lineStatusRangePath = AppConstants.Tfl.LINE_STATUS_RANGE_PATH;

    @NotBlank
    private String allTubeStatusesPath = AppConstants.Tfl.ALL_TUBE_STATUSES_PATH;

    @Positive
    private int connectTimeoutMillis = 1000;

    @Positive
    private int readTimeoutMillis = 2000;

    @Positive
    private int maxInFlight = 200;

    private String appId;
    private String appKey;
    @Valid
    private SyntheticFaultProperties syntheticFault = SyntheticFaultProperties.DISABLED;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public String getLineStatusPath() {
        return lineStatusPath;
    }

    public void setLineStatusPath(String lineStatusPath) {
        this.lineStatusPath = lineStatusPath;
    }

    public String getLineStatusRangePath() {
        return lineStatusRangePath;
    }

    public void setLineStatusRangePath(String lineStatusRangePath) {
        this.lineStatusRangePath = lineStatusRangePath;
    }

    public String getAllTubeStatusesPath() {
        return allTubeStatusesPath;
    }

    public void setAllTubeStatusesPath(String allTubeStatusesPath) {
        this.allTubeStatusesPath = allTubeStatusesPath;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public int getMaxInFlight() {
        return maxInFlight;
    }

    public void setMaxInFlight(int maxInFlight) {
        this.maxInFlight = maxInFlight;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public SyntheticFaultProperties getSyntheticFault() {
        return syntheticFault;
    }

    public void setSyntheticFault(SyntheticFaultProperties syntheticFault) {
        this.syntheticFault = syntheticFault == null ? SyntheticFaultProperties.DISABLED : syntheticFault;
    }
}
