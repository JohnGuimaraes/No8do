package com.no8do.api.agent;

import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AgentAuditEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentAuditEventListener.class);

    private final AgentAuditTrailService auditTrailService;
    private final AgentGatewayMetrics metrics;

    public AgentAuditEventListener(AgentAuditTrailService auditTrailService, AgentGatewayMetrics metrics) {
        this.auditTrailService = auditTrailService;
        this.metrics = metrics;
    }

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void record(AgentEvent event) {
        try {
            if (auditTrailService.record(event)) metrics.auditPersisted(event.type());
        } catch (RuntimeException failure) {
            metrics.auditPersistenceFailed(event.type());
            LOGGER.error("Falha ao persistir AgentAuditEntry: eventId={}, type={}",
                    event.eventId(), event.type(), failure);
            throw failure;
        }
    }
}
