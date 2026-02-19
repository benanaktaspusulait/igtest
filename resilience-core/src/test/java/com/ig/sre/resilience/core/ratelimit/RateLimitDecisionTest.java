package com.ig.sre.resilience.core.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitDecisionTest {

    @Test
    void permitCreatesAllowedDecision() {
        RateLimitDecision decision = RateLimitDecision.permit();
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.retryAfterSeconds()).isZero();
    }

    @Test
    void denyClampsRetryAfterToAtLeastOneSecond() {
        assertThat(RateLimitDecision.deny(0).retryAfterSeconds()).isEqualTo(1);
        assertThat(RateLimitDecision.deny(-5).retryAfterSeconds()).isEqualTo(1);
        assertThat(RateLimitDecision.deny(7).retryAfterSeconds()).isEqualTo(7);
    }
}
