package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AgentSessionRevocationAuditIntegrationTests {
    @Autowired private AgentSessionRevocationService revocationService;
    @Autowired private AgentSessionRegistry registry;
    @Autowired private AgentSessionRepository sessionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @SpyBean private AgentAuditEntryRepository auditEntryRepositorySpy;
    @SpyBean private AgentEventPublisher eventPublisher;

    @Test
    void revokePersistsExactlyOneSafeAuditWithWorkspaceAndRepeatedCallDoesNotDuplicateIt() throws Exception {
        Fixture fixture = createFixture("transactional-audit", true);
        clearInvocations(eventPublisher);

        AgentSessionRevocationResult first = revocationService.revoke(fixture.sessionId(), fixture.actor().getId());
        AgentSession persisted = sessionRepository.findById(fixture.sessionId()).orElseThrow();
        org.mockito.ArgumentCaptor<AgentEvent> eventCaptor = org.mockito.ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventPublisher, times(1)).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo(AgentEventType.AGENT_SESSION_REVOKED);
        assertThat(eventCaptor.getValue().eventId()).isEqualTo(
                AgentSessionRevocationEventId.forSession(fixture.sessionId()));

        clearInvocations(eventPublisher);
        AgentSessionRevocationResult repeated = revocationService.revoke(fixture.sessionId(), fixture.actor().getId());
        verifyNoInteractions(eventPublisher);

        assertThat(first.newlyRevoked()).isTrue();
        assertThat(repeated.newlyRevoked()).isFalse();
        assertThat(repeated.revokedAt()).isEqualTo(first.revokedAt());
        assertThat(repeated.revokedByUserId()).isEqualTo(fixture.actor().getId());
        assertThat(persisted.getRevokedAt()).isEqualTo(first.revokedAt());
        assertThat(persisted.getRevokedByUserId()).isEqualTo(fixture.actor().getId());

        UUID eventId = AgentSessionRevocationEventId.forSession(fixture.sessionId());
        assertThat(jdbcTemplate.queryForObject("select count(*) from agent_audit_entries where event_id = ?",
                Long.class, eventId)).isEqualTo(1L);
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                select event_type, session_id, user_id, workspace_id, occurred_at, metadata::text as metadata
                from agent_audit_entries where event_id = ?
                """, eventId);
        assertThat(row.get("event_type")).isEqualTo(AgentAuditEventType.AGENT_SESSION_REVOKED.name());
        assertThat(row.get("session_id")).isEqualTo(fixture.sessionId());
        assertThat(row.get("user_id")).isEqualTo(fixture.owner().getId());
        assertThat(row.get("workspace_id")).isEqualTo(fixture.workspace().getId());

        JsonNode metadata = objectMapper.readTree((String) row.get("metadata"));
        assertThat(metadata.path("targetSessionId").asText()).isEqualTo(fixture.sessionId().toString());
        assertThat(metadata.path("actorUserId").asText()).isEqualTo(fixture.actor().getId().toString());
        assertThat(metadata.path("workspaceId").asText()).isEqualTo(fixture.workspace().getId().toString());
        assertThat(metadata.path("occurredAt").asText()).isEqualTo(first.revokedAt().toString());
        assertThat(fieldNames(metadata)).containsExactlyInAnyOrder(
                "targetSessionId", "actorUserId", "workspaceId", "occurredAt");
    }

    @Test
    void nullWorkspaceIsStoredAsNullAndOmittedFromSafeMetadata() throws Exception {
        Fixture fixture = createFixture("transactional-audit-null", false);
        AgentSessionRevocationResult result = revocationService.revoke(
                fixture.sessionId(), fixture.owner().getId());

        UUID eventId = AgentSessionRevocationEventId.forSession(fixture.sessionId());
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select workspace_id, metadata::text as metadata from agent_audit_entries where event_id = ?", eventId);
        assertThat(row.get("workspace_id")).isNull();
        JsonNode metadata = objectMapper.readTree((String) row.get("metadata"));
        assertThat(metadata.has("workspaceId")).isFalse();
        assertThat(metadata.path("occurredAt").asText()).isEqualTo(result.revokedAt().toString());
    }

    @Test
    void auditPersistenceFailureRollsBackSessionRevocation() {
        Fixture fixture = createFixture("transactional-audit-failure", true);
        doThrow(new DataIntegrityViolationException("simulated audit insert failure"))
                .when(auditEntryRepositorySpy).insertIfEventAbsent(any(UUID.class), any(UUID.class),
                        eq(AgentAuditEventType.AGENT_SESSION_REVOKED.name()), eq(fixture.sessionId()),
                        eq(fixture.owner().getId()), eq(fixture.workspace().getId()),
                        any(Instant.class), anyString());

        assertThatThrownBy(() -> revocationService.revoke(fixture.sessionId(), fixture.actor().getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        AgentSession unchanged = sessionRepository.findById(fixture.sessionId()).orElseThrow();
        assertThat(unchanged.getRevokedAt()).isNull();
        assertThat(unchanged.getRevokedByUserId()).isNull();
        assertThat(jdbcTemplate.queryForObject("select count(*) from agent_audit_entries where event_id = ?",
                Long.class, AgentSessionRevocationEventId.forSession(fixture.sessionId()))).isZero();
    }

    private Fixture createFixture(String prefix, boolean withWorkspace) {
        User owner = createUser(prefix + "-owner");
        User actor = withWorkspace ? createUser(prefix + "-admin") : owner;
        Workspace workspace = withWorkspace ? workspaceRepository.save(new Workspace(prefix + " workspace")) : null;
        if (workspace != null) {
            workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.MEMBER));
            workspaceMemberRepository.save(new WorkspaceMember(workspace, actor, WorkspaceRole.ADMIN));
        }
        AgentSessionRegistrationRequest request = new AgentSessionRegistrationRequest("revocation-audit", "1.0",
                workspace == null ? null : workspace.getId(), AgentTransport.MCP,
                UUID.randomUUID().toString().replace("-", "").repeat(2));
        UUID sessionId = registry.register(owner.getId(), request).sessionId();
        return new Fixture(owner, actor, workspace, sessionId);
    }

    private User createUser(String username) {
        String uniqueSuffix = UUID.randomUUID().toString();
        return userRepository.save(new User(username + "-" + uniqueSuffix,
                username + "-" + uniqueSuffix + "@example.test", "hash"));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private record Fixture(User owner, User actor, Workspace workspace, UUID sessionId) {}
}
