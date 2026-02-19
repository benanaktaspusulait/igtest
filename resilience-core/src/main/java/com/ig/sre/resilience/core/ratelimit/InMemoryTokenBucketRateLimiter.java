package com.ig.sre.resilience.core.ratelimit;

import com.ig.sre.resilience.core.policy.ResiliencePolicy;
import com.ig.sre.resilience.core.clock.ResilienceClock;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class InMemoryTokenBucketRateLimiter implements RateLimiter {

    private static final Duration DEFAULT_IDLE_EVICTION_AFTER = Duration.ofMinutes(30);
    private static final int DEFAULT_CLEANUP_INTERVAL = 512;

    private final Map<String, BucketState> buckets = new ConcurrentHashMap<>();
    private final Duration idleEvictionAfter;
    private final int cleanupInterval;
    private final AtomicInteger callsSinceCleanup = new AtomicInteger();

    public InMemoryTokenBucketRateLimiter() {
        this(DEFAULT_IDLE_EVICTION_AFTER, DEFAULT_CLEANUP_INTERVAL);
    }

    public InMemoryTokenBucketRateLimiter(Duration idleEvictionAfter, int cleanupInterval) {
        this.idleEvictionAfter = Objects.requireNonNull(idleEvictionAfter, "idleEvictionAfter must not be null");
        if (idleEvictionAfter.isZero() || idleEvictionAfter.isNegative()) {
            throw new IllegalArgumentException("idleEvictionAfter must be positive");
        }
        if (cleanupInterval <= 0) {
            throw new IllegalArgumentException("cleanupInterval must be > 0");
        }
        this.cleanupInterval = cleanupInterval;
    }

    @Override
    public RateLimitDecision tryAcquire(String key, ResiliencePolicy.RateLimitPolicy policy, ResilienceClock clock) {
        if (key == null || key.isBlank()) {
            return RateLimitDecision.permit();
        }

        Instant now = clock.now();
        maybeCleanup(now);

        BucketState state = buckets.computeIfAbsent(key, ignored -> new BucketState(policy.permitsPerMinute(), now));
        return state.tryAcquire(policy.permitsPerMinute(), now);
    }

    private void maybeCleanup(Instant now) {
        if (buckets.isEmpty()) {
            return;
        }

        if (callsSinceCleanup.incrementAndGet() < cleanupInterval) {
            return;
        }

        callsSinceCleanup.set(0);
        buckets.entrySet().removeIf(entry -> entry.getValue().isIdleExpired(now, idleEvictionAfter));
    }

    int bucketCount() {
        return buckets.size();
    }

    private static final class BucketState {
        private double tokens;
        private Instant lastRefillAt;
        private Instant lastSeenAt;

        private BucketState(double initialTokens, Instant lastRefillAt) {
            this.tokens = initialTokens;
            this.lastRefillAt = lastRefillAt;
            this.lastSeenAt = lastRefillAt;
        }

        private synchronized RateLimitDecision tryAcquire(double capacity, Instant now) {
            double refillPerSecond = capacity / 60.0d;
            lastSeenAt = now;
            refillTokens(capacity, refillPerSecond, now);

            if (tokens >= 1.0d) {
                tokens -= 1.0d;
                return RateLimitDecision.permit();
            }

            double tokensNeeded = 1.0d - tokens;
            long retryAfterSeconds = (long) Math.ceil(tokensNeeded / refillPerSecond);
            return RateLimitDecision.deny(retryAfterSeconds);
        }

        private synchronized boolean isIdleExpired(Instant now, Duration idleEvictionAfter) {
            Duration idle = Duration.between(lastSeenAt, now);
            return !idle.isNegative() && idle.compareTo(idleEvictionAfter) >= 0;
        }

        private void refillTokens(double capacity, double refillPerSecond, Instant now) {
            Duration elapsed = Duration.between(lastRefillAt, now);
            if (elapsed.isNegative() || elapsed.isZero()) {
                return;
            }

            double elapsedSeconds = elapsed.toNanos() / 1_000_000_000.0d;
            double refill = elapsedSeconds * refillPerSecond;

            tokens = Math.min(capacity, tokens + refill);
            lastRefillAt = now;
        }
    }
}
