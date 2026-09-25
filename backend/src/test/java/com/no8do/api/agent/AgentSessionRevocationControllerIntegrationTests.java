package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.ServletException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class AgentSessionRevocationControllerIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationEvents applicationEvents;
    @Autowired private AgentSessionRegistry registry;
    @Autowired private AgentSessionRepository sessionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MeterRegistry meterRegistry;
    @SpyBean private AgentAuditEntryRepository auditEntryRepositorySpy;

    @Test
    void endpointRequiresAuthenticationAndUsesWorkspaceManagerAuthorizationWithIndistinguishableNotFound()
            throws Exception {
        Fixture owner = createWorkspaceFixture("owner", WorkspaceRole.OWNER, true);
        Fixture admin = createWorkspaceFixture("admin", WorkspaceRole.ADMIN, true);
        Fixture member = createWorkspaceFixture("member", WorkspaceRole.MEMBER, true);
        Fixture viewer = createWorkspaceFixture("viewer", WorkspaceRole.VIEWER, true);
        Fixture noMembership = createWorkspaceFixture("no-membership", null, false);

        revoke(owner.sessionId(), owner.actor()).andExpect(MockMvcResultMatchers.status().isNoContent());
        revoke(admin.sessionId(), admin.actor()).andExpect(MockMvcResultMatchers.status().isNoContent());
        revoke(member.sessionId(), member.actor()).andExpect(MockMvcResultMatchers.status().isNotFound());
        revoke(viewer.sessionId(), viewer.actor()).andExpect(MockMvcResultMatchers.status().isNotFound());
        revoke(noMembership.sessionId(), noMembership.actor()).andExpect(MockMvcResultMatchers.status().isNotFound());
        revoke(UUID.randomUUID(), admin.actor()).andExpect(MockMvcResultMatchers.status().isNotFound());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/agent-sessions/{sessionId}/revoke", UUID.randomUUID())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void nullWorkspaceAllowsOnlySessionOwner() throws Exception {
        User owner = createUser("null-workspace-owner");
        User other = createUser("null-workspace-other");
        UUID sessionId = register(owner, null);

        revoke(sessionId, owner).andExpect(MockMvcResultMatchers.status().isNoContent());
        revoke(sessionId, other).andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void firstTransitionCommitsAuditThenPublishesOneSafeEventAndIncrementsUntaggedMetricOnce()
            throws Exception {
        Fixture fixture = createWorkspaceFixture("idempotent-event", WorkspaceRole.ADMIN, true);
        double metricBefore = metricCount();

        MvcResult firstResponse = revoke(fixture.sessionId(), fixture.actor())
                .andExpect(MockMvcResultMatchers.status().isNoContent()).andReturn();
        AgentSession persisted = sessionRepository.findById(fixture.sessionId()).orElseThrow();
        UUID eventId = AgentSessionRevocationEventId.forSession(fixture.sessionId());

        assertThat(firstResponse.getResponse().getContentAsString()).isEmpty();
        assertThat(persisted.getRevokedAt()).isNotNull();
        assertThat(persisted.getRevokedByUserId()).isEqualTo(fixture.actor().getId());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from agent_audit_entries where event_id = ?", Long.class, eventId)).isEqualTo(1L);
        List<AgentEvent> revokedEvents = revokedEvents(fixture.sessionId());
        assertThat(revokedEvents).hasSize(1);
        AgentEvent event = revokedEvents.get(0);
        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.workspaceId()).isEqualTo(fixture.workspace().getId());
        assertThat(event.occurredAt()).isEqualTo(persisted.getRevokedAt());
        assertThat(event.metadata()).isEqualTo(new AgentEventMetadata.SessionRevoked(
                fixture.sessionId(), fixture.actor().getId(), fixture.workspace().getId(), persisted.getRevokedAt()));
        assertThat(event.metadata().toString()).doesNotContain("token", "PAT", "fingerprint", "credential", "email");
        assertThat(metricCount()).isEqualTo(metricBefore + 1);
        assertThat(meterRegistry.get(AgentGatewayMetrics.SESSIONS_REVOKED).counter().getId().getTags()).isEmpty();

        revoke(fixture.sessionId(), fixture.actor()).andExpect(MockMvcResultMatchers.status().isNoContent());
        AgentSession afterRepeat = sessionRepository.findById(fixture.sessionId()).orElseThrow();
        assertThat(afterRepeat.getRevokedAt()).isEqualTo(persisted.getRevokedAt());
        assertThat(afterRepeat.getRevokedByUserId()).isEqualTo(persisted.getRevokedByUserId());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from agent_audit_entries where event_id = ?", Long.class, eventId)).isEqualTo(1L);
        assertThat(revokedEvents(fixture.sessionId())).hasSize(1);
        assertThat(metricCount()).isEqualTo(metricBefore + 1);
    }

    @Test
    void auditFailureRollsBackRevocationAndEmitsNoRealtimeEventOrMetric() throws Exception {
        Fixture fixture = createWorkspaceFixture("audit-rollback", WorkspaceRole.ADMIN, true);
        UUID eventId = AgentSessionRevocationEventId.forSession(fixture.sessionId());
        double metricBefore = metricCount();
        doThrow(new DataIntegrityViolationException("simulated mandatory audit failure"))
                .when(auditEntryRepositorySpy).insertIfEventAbsent(any(UUID.class), eq(eventId),
                        eq(AgentAuditEventType.AGENT_SESSION_REVOKED.name()), eq(fixture.sessionId()),
                        eq(fixture.owner().getId()), eq(fixture.workspace().getId()),
                        any(Instant.class), anyString());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> revoke(fixture.sessionId(), fixture.actor()))
                .isInstanceOf(ServletException.class)
                .hasRootCauseInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseMessage("simulated mandatory audit failure");

        AgentSession unchanged = sessionRepository.findById(fixture.sessionId()).orElseThrow();
        assertThat(unchanged.getRevokedAt()).isNull();
        assertThat(unchanged.getRevokedByUserId()).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from agent_audit_entries where event_id = ?", Long.class, eventId)).isZero();
        assertThat(revokedEvents(fixture.sessionId())).isEmpty();
        assertThat(metricCount()).isEqualTo(metricBefore);
    }

    private ResultActions revoke(UUID sessionId, User actor) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/agent-sessions/{sessionId}/revoke", sessionId)
                .with(SecurityMockMvcRequestPostProcessors.user(new No8doUserDetails(actor)))
                .with(SecurityMockMvcRequestPostProcessors.csrf()));
    }

    private Fixture createWorkspaceFixture(String prefix, WorkspaceRole actorRole, boolean addMembership) {
        User owner = createUser(prefix + "-owner");
        User actor = createUser(prefix + "-actor");
        Workspace workspace = workspaceRepository.save(new Workspace(prefix + " workspace"));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.MEMBER));
        if (addMembership) workspaceMemberRepository.save(new WorkspaceMember(workspace, actor, actorRole));
        UUID sessionId = register(owner, workspace.getId());
        return new Fixture(owner, actor, workspace, sessionId);
    }

    private UUID register(User owner, UUID workspaceId) {
        String fingerprint = UUID.randomUUID().toString().replace("-", "").repeat(2);
        return registry.register(owner.getId(), new AgentSessionRegistrationRequest(
                "public-revoke-test", "1.0", workspaceId, AgentTransport.MCP, fingerprint)).sessionId();
    }

    private User createUser(String prefix) {
        String suffix = UUID.randomUUID().toString();
        String username = prefix + "-" + suffix;
        return userRepository.save(new User(username, username + "@example.test", "hash"));
    }

    private List<AgentEvent> revokedEvents(UUID sessionId) {
        return applicationEvents.stream(AgentEvent.class)
                .filter(event -> event.sessionId().equals(sessionId)
                        && event.type() == AgentEventType.AGENT_SESSION_REVOKED)
                .toList();
    }

    private double metricCount() {
        return meterRegistry.counter(AgentGatewayMetrics.SESSIONS_REVOKED).count();
    }

    private record Fixture(User owner, User actor, Workspace workspace, UUID sessionId) {}
}
