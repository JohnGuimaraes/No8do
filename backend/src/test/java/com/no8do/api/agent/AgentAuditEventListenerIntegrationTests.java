package com.no8do.api.agent;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class AgentAuditEventListenerIntegrationTests {
    @Autowired private AgentEventPublisher eventPublisher;
    @Autowired private AgentEventFactory eventFactory;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean private AgentAuditTrailService auditTrailService;

    @Test
    void persistedEventsWaitForCommitAndRollbackDropsThemWhileDenialsRemainAuditable() {
        AgentSession session = new AgentSession(UUID.randomUUID(), UUID.randomUUID(), null,
                new AgentClientIdentity("audit-test", "1"), AgentTransport.MCP,
                new No8doAgentProtocolProvider().current(), UUID.randomUUID().toString().replace("-", "")
                        + UUID.randomUUID().toString().replace("-", ""));
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        AgentEvent connected = eventFactory.connected(session);
        transactions.executeWithoutResult(status -> {
            eventPublisher.publish(connected);
            verifyNoInteractions(auditTrailService);
        });
        verify(auditTrailService).record(connected);

        clearInvocations(auditTrailService);
        AgentEvent modeChanged = eventFactory.runtimeModeChanged(session, AgentRuntimeMode.FULL,
                AgentRuntimeMode.RETRIEVAL);
        transactions.executeWithoutResult(status -> {
            eventPublisher.publish(modeChanged);
            status.setRollbackOnly();
        });
        verifyNoInteractions(auditTrailService);

        AgentEvent capabilityDenied = eventFactory.capabilityDenied(session, AgentCapability.REPLAY_CREATE);
        transactions.executeWithoutResult(status -> {
            eventPublisher.publish(capabilityDenied);
            verify(auditTrailService).record(capabilityDenied);
            status.setRollbackOnly();
        });
        verify(auditTrailService).record(capabilityDenied);
    }
}
