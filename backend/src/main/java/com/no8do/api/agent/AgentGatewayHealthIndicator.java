package com.no8do.api.agent;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports whether the core Agent Gateway components are available and wired.
 * Activity levels such as connected sessions and SSE subscribers are not health signals.
 */
@Component("agentGateway")
public class AgentGatewayHealthIndicator implements HealthIndicator {
    public AgentGatewayHealthIndicator(AgentSessionRegistry sessionRegistry,
            AgentSessionPresenceService presenceService, AgentEventPublisher eventPublisher,
            AgentEventStreamHub eventStreamHub) {
        // Constructor injection makes missing core Gateway components fail application wiring.
    }

    @Override
    public Health health() {
        return Health.up().build();
    }
}
