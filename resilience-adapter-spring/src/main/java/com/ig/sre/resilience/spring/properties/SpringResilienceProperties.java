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
public class SpringResilienceProperties {

    @Valid
    @NotEmpty
    private Map<String, DependencyPolicyProperties> policies = Map.of();

    public Map<String, DependencyPolicyProperties> getPolicies() {
        return copyPolicies(policies);
    }

    public void setPolicies(Map<String, DependencyPolicyProperties> policies) {
        this.policies = copyPolicies(Objects.requireNonNull(policies, "policies must not be null"));
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
