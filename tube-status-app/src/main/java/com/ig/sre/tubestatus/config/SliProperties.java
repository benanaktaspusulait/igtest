package com.ig.sre.tubestatus.config;

import com.ig.sre.tubestatus.common.AppConstants;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = AppConstants.PropertyPrefixes.SLI)
@Validated
public record SliProperties(@NotNull @DefaultValue("PT5M") Duration freshnessThreshold) {
}
