package com.ig.sre.resilience.core.ratelimit;

import com.ig.sre.resilience.core.policy.ResiliencePolicy;
import com.ig.sre.resilience.core.clock.ResilienceClock;

public interface RateLimiter {

    RateLimitDecision tryAcquire(String key, ResiliencePolicy.RateLimitPolicy policy, ResilienceClock clock);
}
