package com.no8do.api.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public final class SpringAgentEventPublisher implements AgentEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpringAgentEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;
    private final AgentGatewayMetrics metrics;

    public SpringAgentEventPublisher(ApplicationEventPublisher applicationEventPublisher, AgentGatewayMetrics metrics) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.metrics = metrics;
    }

    @Override
    public void publish(AgentEvent event) {
        if (isAuthorizationDenial(event)) {
            publishSafely(event);
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishSafely(event);
                }
            });
            return;
        }
        publishSafely(event);
    }

    private static boolean isAuthorizationDenial(AgentEvent event) {
        return event.type() == AgentEventType.CAPABILITY_DENIED
                || event.type() == AgentEventType.POLICY_DENIED;
    }

    private void publishSafely(AgentEvent event) {
        try {
            applicationEventPublisher.publishEvent(event);
            metrics.eventPublished(event.type());
        } catch (RuntimeException listenerFailure) {
            metrics.eventListenerFailed(event.type());
            LOGGER.warn("Listener de AgentEvent falhou após a operação de domínio: eventId={}, type={}, failureType={}",
                    event.eventId(), event.type(), listenerFailure.getClass().getSimpleName());
        }
    }
}
