package com.ig.sre.resilience.spring.config;

import com.ig.sre.resilience.core.clock.ResilienceClock;
import com.ig.sre.resilience.core.executor.ResilientExecutor;
import com.ig.sre.resilience.core.policy.PolicyProvider;
import com.ig.sre.resilience.core.ratelimit.RateLimitDecision;
import com.ig.sre.resilience.core.ratelimit.RateLimiter;
import com.ig.sre.resilience.core.telemetry.NoOpTelemetry;
import com.ig.sre.resilience.core.telemetry.Telemetry;
import com.ig.sre.resilience.spring.telemetry.MicrometerTelemetry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringResilienceAutoConfigurationTest {

    private static final String[] MINIMAL_POLICY_PROPERTIES = {
            "resilience.policies.tfl.timeout-ms=2000",
            "resilience.policies.tfl.retry.max-retries=3",
            "resilience.policies.tfl.circuit-breaker.failure-threshold=5",
            "resilience.policies.tfl.rate-limit.permits-per-minute=100"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpringResilienceAutoConfiguration.class))
            .withPropertyValues(MINIMAL_POLICY_PROPERTIES);

    @Test
    void createsNoOpTelemetryWhenMeterRegistryIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PolicyProvider.class);
            assertThat(context).hasSingleBean(RateLimiter.class);
            assertThat(context).hasSingleBean(ResilienceClock.class);
            assertThat(context).hasSingleBean(Telemetry.class);
            assertThat(context).hasSingleBean(ResilientExecutor.class);
            assertThat(context.getBean(Telemetry.class)).isInstanceOf(NoOpTelemetry.class);
        });
    }

    @Test
    void createsMicrometerTelemetryWhenMeterRegistryIsAvailable() {
        contextRunner
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> assertThat(context.getBean(Telemetry.class)).isInstanceOf(MicrometerTelemetry.class));
    }

    @Test
    void allowsCustomBeanOverrides() {
        contextRunner
                .withUserConfiguration(CustomOverrideConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(RateLimiter.class);
                    assertThat(context.getBean(RateLimiter.class)).isSameAs(CustomOverrideConfiguration.CUSTOM_RATE_LIMITER);
                    assertThat(context).hasSingleBean(Telemetry.class);
                    assertThat(context.getBean(Telemetry.class)).isSameAs(CustomOverrideConfiguration.CUSTOM_TELEMETRY);
                });
    }

    @Test
    void failsFastWhenDependencyPolicyIsMissing() {
        contextRunner.run(context -> {
            PolicyProvider provider = context.getBean(PolicyProvider.class);
            assertThatThrownBy(() -> provider.resolve("unknown"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("No resilience policy configured for dependency: unknown");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomOverrideConfiguration {

        static final RateLimiter CUSTOM_RATE_LIMITER = (key, policy, clock) -> RateLimitDecision.permit();
        static final Telemetry CUSTOM_TELEMETRY = new NoOpTelemetry();

        @Bean
        RateLimiter customRateLimiter() {
            return CUSTOM_RATE_LIMITER;
        }

        @Bean
        Telemetry customTelemetry() {
            return CUSTOM_TELEMETRY;
        }
    }
}
