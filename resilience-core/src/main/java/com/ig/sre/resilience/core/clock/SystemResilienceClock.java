package com.ig.sre.resilience.core.clock;

import java.time.Duration;
import java.time.Instant;

public class SystemResilienceClock implements ResilienceClock {

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public void sleep(Duration duration) throws InterruptedException {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return;
        }
        Thread.sleep(duration.toMillis());
    }
}
