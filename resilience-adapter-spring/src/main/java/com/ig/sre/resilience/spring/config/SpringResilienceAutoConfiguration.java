package com.ig.sre.resilience.spring.config;

import com.ig.sre.resilience.core.clock.ResilienceClock;
import com.ig.sre.resilience.core.clock.SystemResilienceClock;
import com.ig.sre.resilience.core.executor.ResilientExecutor;
import com.ig.sre.resilience.core.policy.PolicyProvider;
import com.ig.sre.resilience.core.policy.ResiliencePolicy;
import com.ig.sre.resilience.core.ratelimit.InMemoryTokenBucketRateLimiter;
import com.ig.sre.resilience.core.ratelimit.RateLimiter;
import com.ig.sre.resilience.core.telemetry.NoOpTelemetry;
import com.ig.sre.resilience.core.telemetry.Telemetry;
import com.ig.sre.resilience.spring.properties.SpringResilienceProperties;
import com.ig.sre.resilience.spring.telemetry.MicrometerTelemetry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Map;

@AutoConfiguration
@EnableConfigurationProperties(SpringResilienceProperties.class)
public class SpringResilienceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PolicyProvider policyProvider(SpringResilienceProperties properties) {
        Map<String, ResiliencePolicy> policies = properties.toPolicies();
        return dependencyKey -> {
            ResiliencePolicy policy = policies.get(dependencyKey);
            if (policy == null) {
                throw new IllegalArgumentException(
                        SpringAdapterConstants.Config.MISSING_POLICY_MESSAGE_PREFIX + dependencyKey
                );
            }
            return policy;
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimiter rateLimiter() {
        return new InMemoryTokenBucketRateLimiter();
    }

    @Bean
    @ConditionalOnMissingBean
    public ResilienceClock resilienceClock() {
        return new SystemResilienceClock();
    }

    @Bean
    @ConditionalOnMissingBean
    public Telemetry resilienceTelemetry(
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            ObjectProvider<Tracer> tracerProvider
    ) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            return new NoOpTelemetry();
        }
        return new MicrometerTelemetry(meterRegistry, tracerProvider.getIfAvailable());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public ResilientExecutor resilientExecutor(
            PolicyProvider policyProvider,
            RateLimiter rateLimiter,
            Telemetry telemetry,
            ResilienceClock clock
    ) {
        return new ResilientExecutor(policyProvider, rateLimiter, telemetry, clock);
    }

}
