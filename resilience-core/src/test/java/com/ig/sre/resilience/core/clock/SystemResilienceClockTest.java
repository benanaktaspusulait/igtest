package com.ig.sre.resilience.core.clock;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SystemResilienceClockTest {

    @Test
    void nowReturnsInstant() {
        SystemResilienceClock clock = new SystemResilienceClock();
        assertThat(clock.now()).isNotNull();
    }

    @Test
    void sleepIgnoresNullAndNonPositiveDurations() {
        SystemResilienceClock clock = new SystemResilienceClock();
        Duration zero = Duration.ZERO;
        Duration negativeOneMillis = Duration.ofMillis(-1);

        assertThatCode(() -> clock.sleep(null)).doesNotThrowAnyException();
        assertThatCode(() -> clock.sleep(zero)).doesNotThrowAnyException();
        assertThatCode(() -> clock.sleep(negativeOneMillis)).doesNotThrowAnyException();
    }
}
