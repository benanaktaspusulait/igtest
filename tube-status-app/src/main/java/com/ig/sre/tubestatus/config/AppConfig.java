package com.ig.sre.tubestatus.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ig.sre.tubestatus.api.model.LineStatusResponse;
import com.ig.sre.tubestatus.api.model.UnplannedDisruptionsResponse;
import com.ig.sre.tubestatus.common.AppConstants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({
        TflProperties.class,
        ApiProperties.class,
        CacheProperties.class,
        SliProperties.class
})
public class AppConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient tflRestClient(RestClient.Builder builder, TflProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeoutMillis());
        requestFactory.setReadTimeout(properties.readTimeoutMillis());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    @Qualifier(AppConstants.CacheNames.LINE_STATUS_CACHE_BEAN)
    public Cache<String, LineStatusResponse> lineStatusCache(CacheProperties cacheProperties) {
        return Caffeine.newBuilder()
                .maximumSize(cacheProperties.lineStatus().maximumSize())
                .expireAfterWrite(cacheProperties.lineStatus().expireAfterWrite())
                .build();
    }

    @Bean
    @Qualifier(AppConstants.CacheNames.UNPLANNED_DISRUPTION_CACHE_BEAN)
    public Cache<String, UnplannedDisruptionsResponse> unplannedDisruptionCache(CacheProperties cacheProperties) {
        return Caffeine.newBuilder()
                .maximumSize(cacheProperties.unplannedDisruptions().maximumSize())
                .expireAfterWrite(cacheProperties.unplannedDisruptions().expireAfterWrite())
                .build();
    }
}
