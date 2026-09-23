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

    public SpringAgentEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(AgentEvent event) {
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

    private void publishSafely(AgentEvent event) {
        try {
            applicationEventPublisher.publishEvent(event);
        } catch (RuntimeException listenerFailure) {
            LOGGER.warn("Listener de AgentEvent falhou após a operação de domínio: eventId={}, type={}, failureType={}",
                    event.eventId(), event.type(), listenerFailure.getClass().getSimpleName());
        }
    }
}
