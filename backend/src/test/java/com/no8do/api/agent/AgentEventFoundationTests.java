package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.no8do.api.replay.ReplayUsageResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AgentEventFoundationTests {
    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");
    private final AgentEventFactory factory = new AgentEventFactory(Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void factoryBuildsImmutableServerIdentifiedEventsWithTypedSafeMetadata() {
        UUID replayId = UUID.randomUUID();
        AgentSession session = session();

        AgentEvent connected = factory.connected(session);
        AgentEvent modeChanged = factory.runtimeModeChanged(session, AgentRuntimeMode.FULL, AgentRuntimeMode.RETRIEVAL);
        AgentEvent usage = factory.replayUsageRecorded(session, replayId, 3, ReplayUsageResult.SUCCESS);

        assertThat(connected.eventId()).isNotNull();
        assertThat(factory.connected(session).eventId()).isNotEqualTo(connected.eventId());
        assertThat(connected.occurredAt()).isEqualTo(NOW);
        assertThat(connected.type()).isEqualTo(AgentEventType.AGENT_CONNECTED);
        assertThat(connected.sessionId()).isEqualTo(session.getId());
        assertThat(connected.userId()).isEqualTo(session.getUserId());
        assertThat(connected.workspaceId()).isEqualTo(session.getWorkspaceId());
        assertThat(modeChanged.metadata()).isEqualTo(
                new AgentEventMetadata.RuntimeModeChanged(AgentRuntimeMode.FULL, AgentRuntimeMode.RETRIEVAL));
        assertThat(usage.metadata()).isEqualTo(
                new AgentEventMetadata.ReplayUsageRecorded(replayId, 3, ReplayUsageResult.SUCCESS));
        assertThat(connected.toString()).doesNotContain("fingerprint-secret", "raw-mcp-session-secret", "Bearer", "PAT");
        assertThatThrownBy(() -> new AgentEvent(UUID.randomUUID(), AgentEventType.AGENT_CONNECTED,
                session.getId(), session.getUserId(), session.getWorkspaceId(), NOW,
                new AgentEventMetadata.RuntimeModeChanged(AgentRuntimeMode.FULL, AgentRuntimeMode.OFF)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void policyDenialMetadataUsesOnlySafeGenericReason() {
        AgentEvent event = factory.policyDenied(session(), new AgentPolicyDecision("workspace-isolation-required",
                AgentPolicyDecisionType.DENY, "unsafe request detail must not escape"));

        assertThat(event.metadata()).isEqualTo(new AgentEventMetadata.PolicyDenied("workspace-isolation-required",
                "Uma policy ENFORCED negou a operação."));
        assertThat(event.toString()).doesNotContain("unsafe request detail", "fingerprint-secret");
    }

    @Test
    void springPublisherWaitsForCommitAndDropsEventsOnRollback() {
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
        AgentEventPublisher publisher = new SpringAgentEventPublisher(springPublisher);
        AgentEvent event = factory.connected(session());

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        publisher.publish(event);
        verifyNoInteractions(springPublisher);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync ->
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(springPublisher);
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        publisher.publish(event);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(springPublisher).publishEvent(event);
    }

    @Test
    void springPublisherContainsFutureListenerFailures() {
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("listener failure"))
                .when(springPublisher).publishEvent(org.mockito.ArgumentMatchers.any(AgentEvent.class));
        AgentEventPublisher publisher = new SpringAgentEventPublisher(springPublisher);

        assertThatCode(() -> publisher.publish(factory.disconnected(session()))).doesNotThrowAnyException();
    }

    private AgentSession session() {
        return new AgentSession(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new AgentClientIdentity("event-test", "1"), AgentTransport.MCP,
                new No8doAgentProtocolProvider().current(), UUID.randomUUID().toString().replace("-", "")
                        + UUID.randomUUID().toString().replace("-", ""));
    }
}
