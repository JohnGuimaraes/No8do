package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AgentAuditEntryRepositoryTests {
    @Autowired private AgentAuditEntryRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;
    private final AgentAuditMetadataCodec codec = new AgentAuditMetadataCodec(new ObjectMapper());

    @Test
    void persistsAllCanonicalTypesWithServerRecordedTimeAndIdempotentEventId() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-01-02T03:04:05Z");
        AgentAuditTrailService service = new AgentAuditTrailService(repository, codec,
                mock(WorkspaceAuthorizationService.class));
        for (AgentEventType type : AgentEventType.values()) {
            AgentEventMetadata metadata = metadata(type);
            UUID eventId = UUID.randomUUID();
            assertThat(insert(UUID.randomUUID(), eventId, type, sessionId, userId, workspaceId,
                    occurredAt, metadata)).isEqualTo(1);
            assertThat(insert(UUID.randomUUID(), eventId, type, sessionId, userId, workspaceId,
                    occurredAt, metadata)).isZero();

            AgentAuditEntryResponse entry = service.list(userId, sessionId, workspaceId, type,
                    occurredAt, occurredAt, 0, 10).content().get(0);
            assertThat(entry.eventId()).isEqualTo(eventId);
            assertThat(entry.eventType()).isEqualTo(type);
            assertThat(entry.occurredAt()).isEqualTo(occurredAt);
            assertThat(entry.recordedAt()).isNotNull().isAfter(occurredAt);
            assertThat(entry.metadata()).isEqualTo(metadata);
        }
    }

    @Test
    void listingPagesOrdersAndAppliesEveryFilterWithoutCrossingUserBoundary() {
        UUID owner = UUID.randomUUID();
        UUID anotherUser = UUID.randomUUID();
        UUID sessionA = UUID.randomUUID();
        UUID sessionB = UUID.randomUUID();
        UUID workspace = UUID.randomUUID();
        Instant jan1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant jan2 = jan1.plusSeconds(86400);
        Instant jan3 = jan2.plusSeconds(86400);
        insert(UUID.randomUUID(), UUID.randomUUID(), AgentEventType.AGENT_CONNECTED, sessionA,
                owner, workspace, jan1, new AgentEventMetadata.Empty());
        insert(UUID.randomUUID(), UUID.randomUUID(), AgentEventType.POLICY_DENIED, sessionB,
                owner, workspace, jan2, new AgentEventMetadata.PolicyDenied("workspace-isolation-required",
                        "Uma policy ENFORCED negou a operação."));
        insert(UUID.randomUUID(), UUID.randomUUID(), AgentEventType.REPLAY_USAGE_RECORDED, sessionA,
                owner, null, jan3, new AgentEventMetadata.ReplayUsageRecorded(UUID.randomUUID(), 2,
                        com.no8do.api.replay.ReplayUsageResult.SUCCESS));
        insert(UUID.randomUUID(), UUID.randomUUID(), AgentEventType.AGENT_DISCONNECTED, sessionA,
                anotherUser, workspace, jan3.plusSeconds(1), new AgentEventMetadata.Empty());

        WorkspaceAuthorizationService authorization = mock(WorkspaceAuthorizationService.class);
        AgentAuditTrailService service = new AgentAuditTrailService(repository, codec, authorization);
        AgentAuditPageResponse firstPage = service.list(owner, null, null, null, null, null, 0, 2);
        AgentAuditPageResponse secondPage = service.list(owner, null, null, null, null, null, 1, 2);
        AgentAuditPageResponse bySession = service.list(owner, sessionA, null, null, null, null, 0, 10);
        AgentAuditPageResponse byType = service.list(owner, null, null, AgentEventType.POLICY_DENIED,
                null, null, 0, 10);
        AgentAuditPageResponse byRange = service.list(owner, null, null, null, jan2, jan3, 0, 10);
        AgentAuditPageResponse fromOnly = service.list(owner, null, null, null, jan2, null, 0, 10);
        AgentAuditPageResponse toOnly = service.list(owner, null, null, null, null, jan2, 0, 10);
        AgentAuditPageResponse combined = service.list(owner, sessionA, null,
                AgentEventType.REPLAY_USAGE_RECORDED, jan3, jan3, 0, 10);
        AgentAuditPageResponse byWorkspace = service.list(owner, null, workspace, null, null, null, 0, 10);

        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.content()).hasSize(2);
        assertThat(firstPage.content().get(0).occurredAt()).isEqualTo(jan3);
        assertThat(secondPage.content()).hasSize(1);
        assertThat(bySession.content()).hasSize(2);
        assertThat(byType.content()).singleElement().extracting(AgentAuditEntryResponse::eventType)
                .isEqualTo(AgentEventType.POLICY_DENIED);
        assertThat(byRange.content()).hasSize(2);
        assertThat(fromOnly.content()).hasSize(2);
        assertThat(toOnly.content()).hasSize(2);
        assertThat(combined.content()).singleElement()
                .extracting(AgentAuditEntryResponse::eventType).isEqualTo(AgentEventType.REPLAY_USAGE_RECORDED);
        assertThat(byWorkspace.content()).hasSize(2);
        verify(authorization).requireWorkspaceMember(workspace, owner);
    }

    @Test
    void appendOnlyTriggerRejectsUpdatesAndDeletes() {
        UUID userId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        insert(id, eventId, AgentEventType.AGENT_CONNECTED, UUID.randomUUID(), userId, null,
                Instant.parse("2026-01-01T00:00:00Z"), new AgentEventMetadata.Empty());

        Throwable failure = catchThrowable(() -> jdbcTemplate.update(
                "update agent_audit_entries set event_type = ? where id = ?",
                AgentEventType.POLICY_DENIED.name(), id));
        assertThat(causeMessages(failure)).contains("agent audit entries are append-only");
    }

    @Test
    void appendOnlyTriggerRejectsDeletes() {
        UUID id = UUID.randomUUID();
        insert(id, UUID.randomUUID(), AgentEventType.AGENT_CONNECTED, UUID.randomUUID(),
                UUID.randomUUID(), null, Instant.parse("2026-01-01T00:00:00Z"),
                new AgentEventMetadata.Empty());

        Throwable failure = catchThrowable(() -> jdbcTemplate.update(
                "delete from agent_audit_entries where id = ?", id));
        assertThat(causeMessages(failure)).contains("agent audit entries are append-only");
    }

    private int insert(UUID id, UUID eventId, AgentEventType type, UUID sessionId, UUID userId,
            UUID workspaceId, Instant occurredAt, AgentEventMetadata metadata) {
        return repository.insertIfEventAbsent(id, eventId, type.name(), sessionId, userId, workspaceId,
                occurredAt, codec.encode(metadata));
    }

    private static AgentEventMetadata metadata(AgentEventType type) {
        return switch (type) {
            case AGENT_CONNECTED, AGENT_DISCONNECTED -> new AgentEventMetadata.Empty();
            case RUNTIME_MODE_CHANGED -> new AgentEventMetadata.RuntimeModeChanged(
                    AgentRuntimeMode.FULL, AgentRuntimeMode.RETRIEVAL);
            case CAPABILITY_DENIED -> new AgentEventMetadata.CapabilityDenied(
                    AgentCapability.REPLAY_CREATE, AgentRuntimeMode.FULL);
            case POLICY_DENIED -> new AgentEventMetadata.PolicyDenied("workspace-isolation-required",
                    "Uma policy ENFORCED negou a operação.");
            case REPLAY_USAGE_RECORDED -> new AgentEventMetadata.ReplayUsageRecorded(
                    UUID.randomUUID(), 1, com.no8do.api.replay.ReplayUsageResult.SUCCESS);
        };
    }

    private static String causeMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null) messages.append(current.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
