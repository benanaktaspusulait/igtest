package com.ig.sre.resilience.core.policy;

public interface PolicyProvider {

    ResiliencePolicy resolve(String dependencyKey);
}
