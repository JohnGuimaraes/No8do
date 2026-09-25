package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AgentPresenceResolverTests {
    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");
    private static final AgentPresenceProperties THRESHOLDS =
            new AgentPresenceProperties(Duration.ofMinutes(2), Duration.ofMinutes(5));
    private final AgentPresenceResolver resolver = new AgentPresenceResolver();

    @Test
    void derivesConnectedActiveIdleAndDisconnectedDeterministically() {
        assertThat(resolve(NOW, null)).isEqualTo(AgentPresenceStatus.CONNECTED);
        assertThat(resolve(NOW, NOW)).isEqualTo(AgentPresenceStatus.ACTIVE);
        assertThat(resolve(NOW, NOW.minus(Duration.ofMinutes(2)))).isEqualTo(AgentPresenceStatus.ACTIVE);
        assertThat(resolve(NOW, NOW.minus(Duration.ofMinutes(2)).minusNanos(1)))
                .isEqualTo(AgentPresenceStatus.IDLE);
        assertThat(resolve(NOW.minus(Duration.ofMinutes(5)), null)).isEqualTo(AgentPresenceStatus.CONNECTED);
        assertThat(resolve(NOW.minus(Duration.ofMinutes(5)).minusNanos(1), null))
                .isEqualTo(AgentPresenceStatus.DISCONNECTED);
        assertThat(resolve(NOW.minus(Duration.ofMinutes(6)), NOW)).isEqualTo(AgentPresenceStatus.DISCONNECTED);
        assertThat(resolve(NOW, NOW)).isEqualTo(resolve(NOW, NOW));
    }

    @Test
    void historicalSessionWithoutLastSeenFallsBackToRegisteredAt() {
        assertThat(resolver.resolve(NOW.minus(Duration.ofMinutes(5)), null, null, NOW, THRESHOLDS))
                .isEqualTo(AgentPresenceStatus.CONNECTED);
        assertThat(resolver.resolve(NOW.minus(Duration.ofMinutes(5)).minusNanos(1), null, null, NOW, THRESHOLDS))
                .isEqualTo(AgentPresenceStatus.DISCONNECTED);
    }

    @Test
    void explicitDisconnectTakesPriorityOverRecentHeartbeatAndActivity() {
        Instant disconnectedAt = NOW.minusSeconds(1);
        assertThat(resolver.resolve(NOW.minus(Duration.ofHours(1)), NOW, NOW, disconnectedAt, NOW, THRESHOLDS))
                .isEqualTo(AgentPresenceStatus.DISCONNECTED);
        assertThat(resolver.resolve(NOW.minus(Duration.ofHours(1)), NOW, NOW, null, NOW, THRESHOLDS))
                .isEqualTo(AgentPresenceStatus.ACTIVE);
    }

    @Test
    void revocationAlwaysWinsOverDisconnectTimeoutAndRecentActivity() {
        Instant revokedAt = NOW.minusSeconds(10);
        Instant disconnectedAt = NOW.minusSeconds(5);
        assertThat(resolver.resolve(NOW, NOW, NOW, null, revokedAt, NOW, THRESHOLDS))
                .isEqualTo(AgentPresenceStatus.REVOKED);
        assertThat(resolver.resolve(NOW, NOW, NOW, disconnectedAt, revokedAt, NOW, THRESHOLDS))
                .isEqualTo(AgentPresenceStatus.REVOKED);
        assertThat(resolver.resolve(NOW.minus(Duration.ofHours(1)), NOW.minus(Duration.ofHours(1)),
                NOW.minus(Duration.ofHours(1)), null, revokedAt, NOW, THRESHOLDS))
                .isEqualTo(AgentPresenceStatus.REVOKED);
    }

    @Test
    void defaultsAreConservativeAndInvalidThresholdsFailClearly() {
        AgentPresenceProperties defaults = new AgentPresenceProperties(null, null);
        assertThat(defaults.activeWindow()).isEqualTo(Duration.ofMinutes(2));
        assertThat(defaults.disconnectTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThatThrownBy(() -> new AgentPresenceProperties(Duration.ofMinutes(5), Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("active-window");
        assertThatThrownBy(() -> new AgentPresenceProperties(Duration.ZERO, Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("deve ser positivo");
        assertThatThrownBy(() -> new AgentPresenceProperties(Duration.ofMinutes(1), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("disconnect-timeout");
    }

    private AgentPresenceStatus resolve(Instant lastSeenAt, Instant lastActivityAt) {
        return resolver.resolve(NOW.minus(Duration.ofHours(1)), lastSeenAt, lastActivityAt, NOW, THRESHOLDS);
    }
}
