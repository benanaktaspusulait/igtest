package com.ig.sre.resilience.spring.properties;

import com.ig.sre.resilience.core.policy.ResiliencePolicy;
import com.ig.sre.resilience.spring.config.SpringAdapterConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@ConfigurationProperties(prefix = SpringAdapterConstants.Config.RESILIENCE_PREFIX)
@Validated
public record SpringResilienceProperties(
        @Valid @NotEmpty Map<String, DependencyPolicyProperties> policies
) {
    public SpringResilienceProperties {
        policies = copyPolicies(Objects.requireNonNullElse(policies, Map.of()));
    }

    public Map<String, ResiliencePolicy> toPolicies() {
        Map<String, ResiliencePolicy> mapped = new HashMap<>();
        policies.forEach((key, value) -> mapped.put(key, value.toPolicy()));
        return Map.copyOf(mapped);
    }

    private static Map<String, DependencyPolicyProperties> copyPolicies(Map<String, DependencyPolicyProperties> source) {
        Objects.requireNonNull(source, "policies must not be null");
        Map<String, DependencyPolicyProperties> copied = new HashMap<>();
        source.forEach((key, value) -> copied.put(key, Objects.requireNonNull(value, "dependency policy must not be null")));
        return Map.copyOf(copied);
    }
}
