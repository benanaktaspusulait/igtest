package com.ig.sre.resilience.core.clock;

import java.time.Duration;
import java.time.Instant;

public interface ResilienceClock {

    Instant now();

    void sleep(Duration duration) throws InterruptedException;
}
