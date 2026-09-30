package com.no8do.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class IntegrationBootstrapRateLimiterTests {
    @Test
    void appliesDeterministicWindowLimitsAndExpiresKeys() {
        AdjustableClock clock = new AdjustableClock(Instant.parse("2026-09-29T12:00:00Z"));
        IntegrationBootstrapRateLimiter limiter = new IntegrationBootstrapRateLimiter(clock);
        assertThat(limiter.consume("start", "127.0.0.1", 2, Duration.ofSeconds(10))).isZero();
        assertThat(limiter.consume("start", "127.0.0.1", 2, Duration.ofSeconds(10))).isZero();
        assertThat(limiter.consume("start", "127.0.0.1", 2, Duration.ofSeconds(10))).isEqualTo(10);
        clock.advance(Duration.ofSeconds(10));
        assertThat(limiter.consume("start", "127.0.0.1", 2, Duration.ofSeconds(10))).isZero();
    }

    static final class AdjustableClock extends Clock {
        private final AtomicReference<Instant> instant;
        AdjustableClock(Instant initial) { instant = new AtomicReference<>(initial); }
        void advance(Duration duration) { instant.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
