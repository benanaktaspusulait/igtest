package com.ig.sre.resilience.core.ratelimit;

import com.ig.sre.resilience.core.policy.ResiliencePolicy;
import com.ig.sre.resilience.core.clock.ResilienceClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTokenBucketRateLimiterTest {

    @Test
    void evictsIdleBucketsOnCleanup() {
        MutableClock clock = new MutableClock();
        InMemoryTokenBucketRateLimiter limiter = new InMemoryTokenBucketRateLimiter(Duration.ofSeconds(30), 1);
        ResiliencePolicy.RateLimitPolicy policy = new ResiliencePolicy.RateLimitPolicy(60);

        assertThat(limiter.tryAcquire("tfl|ip-1", policy, clock).allowed()).isTrue();
        assertThat(limiter.bucketCount()).isEqualTo(1);

        clock.plus(Duration.ofSeconds(31));
        assertThat(limiter.tryAcquire("tfl|ip-2", policy, clock).allowed()).isTrue();

        assertThat(limiter.bucketCount()).isEqualTo(1);
    }

    @Test
    void keepsRecentlyUsedBuckets() {
        MutableClock clock = new MutableClock();
        InMemoryTokenBucketRateLimiter limiter = new InMemoryTokenBucketRateLimiter(Duration.ofSeconds(30), 1);
        ResiliencePolicy.RateLimitPolicy policy = new ResiliencePolicy.RateLimitPolicy(60);

        assertThat(limiter.tryAcquire("tfl|ip-1", policy, clock).allowed()).isTrue();
        clock.plus(Duration.ofSeconds(10));
        assertThat(limiter.tryAcquire("tfl|ip-1", policy, clock).allowed()).isTrue();

        clock.plus(Duration.ofSeconds(25));
        assertThat(limiter.tryAcquire("tfl|ip-2", policy, clock).allowed()).isTrue();

        assertThat(limiter.bucketCount()).isEqualTo(2);
    }

    private static final class MutableClock implements ResilienceClock {

        private Instant now = Instant.parse("2026-02-19T10:00:00Z");

        @Override
        public Instant now() {
            return now;
        }

        @Override
        public void sleep(Duration duration) {
            now = now.plus(duration);
        }

        private void plus(Duration duration) {
            now = now.plus(duration);
        }
    }
}
