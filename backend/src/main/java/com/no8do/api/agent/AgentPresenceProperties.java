package com.no8do.api.agent;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "no8do.agent.presence")
public record AgentPresenceProperties(Duration activeWindow, Duration disconnectTimeout) {
    public AgentPresenceProperties {
        activeWindow = activeWindow == null ? Duration.ofMinutes(2) : activeWindow;
        disconnectTimeout = disconnectTimeout == null ? Duration.ofMinutes(5) : disconnectTimeout;
        if (activeWindow.isZero() || activeWindow.isNegative()) {
            throw new IllegalArgumentException("no8do.agent.presence.active-window deve ser positivo.");
        }
        if (disconnectTimeout.isZero() || disconnectTimeout.isNegative()) {
            throw new IllegalArgumentException("no8do.agent.presence.disconnect-timeout deve ser positivo.");
        }
        if (activeWindow.compareTo(disconnectTimeout) >= 0) {
            throw new IllegalArgumentException("active-window deve ser menor que disconnect-timeout.");
        }
    }
}
