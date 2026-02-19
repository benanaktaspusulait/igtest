package com.ig.sre.tubestatus.config;

import com.ig.sre.tubestatus.common.AppConstants;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = AppConstants.PropertyPrefixes.SLI)
@Validated
public class SliProperties {

    @NotNull
    private Duration freshnessThreshold = Duration.ofMinutes(5);

    public Duration getFreshnessThreshold() {
        return freshnessThreshold;
    }

    public void setFreshnessThreshold(Duration freshnessThreshold) {
        this.freshnessThreshold = freshnessThreshold;
    }
}
