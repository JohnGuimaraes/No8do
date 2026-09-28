package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class AgentHubControllerIntegrationTests {
    private static final String ROOT = "/api/workspaces/%s/agents/%s";
    private static final Set<String> FORBIDDEN_FIELDS = Set.of("secret", "secrethash", "credential",
            "publiccredentialid", "agentcredentialid", "pat", "token", "fingerprint", "authorization", "headers");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private AgentRegistryService agentRegistryService;
    @Autowired private AgentCredentialService credentialService;
    @Autowired private AgentSessionRegistry sessionRegistry;
    @Autowired private AgentSessionRepository sessionRepository;
    @Autowired private AgentSessionPresenceService presenceService;
    @Autowired private AgentSessionRevocationService revocationService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<UUID> workspaceIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (UUID workspaceId : workspaceIds) {
                memberRepository.deleteByWorkspaceId(workspaceId);
                workspaceRepository.deleteById(workspaceId);
            }
            for (UUID userId : userIds) userRepository.deleteById(userId);
        });
        workspaceIds.clear();
        userIds.clear();
    }

    @Test
    void workspaceRolesAndScopedAgentLookupProtectAllThreeReadEndpoints() throws Exception {
        Fixture fixture = fixture("hub-rbac");
        User admin = newUser("hub-admin");
        User member = newUser("hub-member");
        User viewer = newUser("hub-viewer");
        User outsider = newUser("hub-outsider");
        User otherWorkspaceAdmin = newUser("hub-other-admin");
        member(fixture.workspaceId(), fixture.owner(), WorkspaceRole.OWNER);
        member(fixture.workspaceId(), admin, WorkspaceRole.ADMIN);
        member(fixture.workspaceId(), member, WorkspaceRole.MEMBER);
        member(fixture.workspaceId(), viewer, WorkspaceRole.VIEWER);
        UUID otherWorkspaceId = workspace("Hub other workspace");
        member(otherWorkspaceId, otherWorkspaceAdmin, WorkspaceRole.ADMIN);
        Agent agent = agent(fixture.workspaceId(), fixture.owner(), "RBAC Agent");
        String base = base(fixture.workspaceId(), agent.getId());

        assertAllEndpoints(base, fixture.owner(), 200);
        assertAllEndpoints(base, admin, 200);
        assertAllEndpoints(base, member, 403);
        assertAllEndpoints(base, viewer, 403);
        assertAllEndpoints(base, outsider, 403);
        assertAllAnonymous(base);

        String wrongWorkspacePath = base(otherWorkspaceId, agent.getId());
        assertAllEndpoints(wrongWorkspacePath, otherWorkspaceAdmin, 404);
    }

    @Test
    void zeroSessionsReturnAnOfflineOverviewEmptySessionPageAndAvailableActivity() throws Exception {
        Fixture fixture = fixture("hub-empty");
        member(fixture.workspaceId(), fixture.owner(), WorkspaceRole.OWNER);
        Agent agent = agent(fixture.workspaceId(), fixture.owner(), "Empty Agent");
        String base = base(fixture.workspaceId(), agent.getId());

        MvcResult overviewResult = mockMvc.perform(get(base + "/overview")
                .with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value(agent.getId().toString()))
                .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.operationalPresence").value("DISCONNECTED"))
                .andExpect(jsonPath("$.totalSessions").value(0))
                .andExpect(jsonPath("$.activeSessions").value(0))
                .andExpect(jsonPath("$.lastSeenAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.lastActivityAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.lastRegisteredAt").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();
        MvcResult sessionsResult = mockMvc.perform(get(base + "/sessions")
                .with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andReturn();
        MvcResult activityResult = mockMvc.perform(get(base + "/activity")
                .with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("AGENT_CREATED"))
                .andReturn();
        assertSafe(overviewResult, sessionsResult, activityResult);
    }

    @Test
    void sessionsAndOverviewAggregatePresenceHistoryAndTimestampsWithoutCrossAgentLeakage() throws Exception {
        Fixture fixture = fixture("hub-multiple");
        member(fixture.workspaceId(), fixture.owner(), WorkspaceRole.OWNER);
        Agent agent = agent(fixture.workspaceId(), fixture.owner(), "Operational Agent");
        AgentCredentialIssueResponse credential = credentialService.create(fixture.workspaceId(), agent.getId(), fixture.owner().getId());
        UUID active = register(fixture, agent, credential, "active");
        UUID connected = register(fixture, agent, credential, "connected");
        UUID idle = register(fixture, agent, credential, "idle");
        UUID stale = register(fixture, agent, credential, "stale");
        UUID disconnected = register(fixture, agent, credential, "disconnected");
        UUID revoked = register(fixture, agent, credential, "revoked");
        Agent otherAgent = agent(fixture.workspaceId(), fixture.owner(), "Other Agent");
        AgentCredentialIssueResponse otherCredential = credentialService.create(
                fixture.workspaceId(), otherAgent.getId(), fixture.owner().getId());
        UUID otherAgentSession = register(fixture, otherAgent, otherCredential, "other-agent");

        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Instant old = now.minusSeconds(900);
        setTimes(active, old, now.minusSeconds(10), now.minusSeconds(20));
        setTimes(connected, old, now.minusSeconds(15), null);
        setTimes(idle, old.plusSeconds(1), now.minusSeconds(30), now.minusSeconds(180));
        setTimes(stale, old.plusSeconds(2), now.minusSeconds(360), null);
        setTimes(disconnected, old.plusSeconds(3), now.minusSeconds(25), now.minusSeconds(15));
        setTimes(revoked, old.plusSeconds(4), now.minusSeconds(5), now.minusSeconds(5));
        setTimes(otherAgentSession, old.plusSeconds(5), now, now);
        presenceService.disconnect(disconnected, fixture.owner().getId());
        revocationService.revoke(revoked, fixture.owner().getId());

        String base = base(fixture.workspaceId(), agent.getId());
        MvcResult sessionsResult = mockMvc.perform(get(base + "/sessions?size=20")
                .with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.content.length()").value(6))
                .andExpect(jsonPath("$.content[?(@.sessionId == '" + disconnected + "')].presenceStatus").value("DISCONNECTED"))
                .andExpect(jsonPath("$.content[?(@.sessionId == '" + revoked + "')].presenceStatus").value("REVOKED"))
                .andExpect(jsonPath("$.content[?(@.sessionId == '" + active + "')].presenceStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.content[?(@.sessionId == '" + connected + "')].presenceStatus").value("CONNECTED"))
                .andExpect(jsonPath("$.content[?(@.sessionId == '" + idle + "')].presenceStatus").value("IDLE"))
                .andExpect(jsonPath("$.content[?(@.sessionId == '" + stale + "')].presenceStatus").value("DISCONNECTED"))
                .andReturn();
        JsonNode sessionContent = objectMapper.readTree(sessionsResult.getResponse().getContentAsString()).get("content");
        List<String> equalRegisteredIds = new ArrayList<>();
        for (JsonNode session : sessionContent) {
            if (session.get("registeredAt").asText().equals(old.toString())) {
                equalRegisteredIds.add(session.get("sessionId").asText());
            }
        }
        assertThat(equalRegisteredIds).containsExactly(active.toString(), connected.toString());
        assertThat(sessionContent.toString()).doesNotContain(otherAgentSession.toString());

        MvcResult overviewResult = mockMvc.perform(get(base + "/overview")
                .with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSessions").value(6))
                .andExpect(jsonPath("$.activeSessions").value(3))
                .andExpect(jsonPath("$.operationalPresence").value("ACTIVE"))
                .andExpect(jsonPath("$.lastSeenAt").value(now.minusSeconds(5).toString()))
                .andExpect(jsonPath("$.lastActivityAt").value(now.minusSeconds(5).toString()))
                .andExpect(jsonPath("$.lastRegisteredAt").value(old.plusSeconds(4).toString()))
                .andReturn();
        agentRegistryService.changeLifecycle(fixture.workspaceId(), agent.getId(), fixture.owner().getId(),
                AgentLifecycleStatus.DISABLED);
        MvcResult disabledOverview = mockMvc.perform(get(base + "/overview")
                .with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("DISABLED"))
                .andExpect(jsonPath("$.operationalPresence").value("ACTIVE"))
                .andReturn();
        assertSafe(sessionsResult, overviewResult, disabledOverview);
    }

    @Test
    void activityCombinesOnlySafeAgentAndSessionAuditWithOrderingAndBoundedLimit() throws Exception {
        Fixture fixture = fixture("hub-activity");
        member(fixture.workspaceId(), fixture.owner(), WorkspaceRole.OWNER);
        Agent agent = agent(fixture.workspaceId(), fixture.owner(), "Activity Agent");
        AgentCredentialIssueResponse credential = credentialService.create(fixture.workspaceId(), agent.getId(), fixture.owner().getId());
        UUID sessionId = register(fixture, agent, credential, "activity-session");
        Agent other = agent(fixture.workspaceId(), fixture.owner(), "Excluded Agent");
        AgentCredentialIssueResponse otherCredential = credentialService.create(fixture.workspaceId(), other.getId(), fixture.owner().getId());
        UUID otherSessionId = register(fixture, other, otherCredential, "activity-other-session");
        credentialService.revoke(fixture.workspaceId(), agent.getId(), credential.id(), fixture.owner().getId());
        agentRegistryService.updateAgent(fixture.workspaceId(), agent.getId(), fixture.owner().getId(),
                "Activity Agent Updated", "updated", null);
        agentRegistryService.changeLifecycle(fixture.workspaceId(), agent.getId(), fixture.owner().getId(),
                AgentLifecycleStatus.DISABLED);

        String base = base(fixture.workspaceId(), agent.getId());
        MvcResult allResult = mockMvc.perform(get(base + "/activity?limit=100")
                .with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode all = objectMapper.readTree(allResult.getResponse().getContentAsString());
        assertThat(all.toString()).contains("AGENT_CREATED", "AGENT_SESSION_BOUND", "AGENT_CONNECTED",
                "AGENT_UPDATED", "AGENT_LIFECYCLE_CHANGED");
        assertThat(all.toString()).doesNotContain("AGENT_CREDENTIAL", credential.credential(), otherSessionId.toString());
        assertThat(all.toString()).contains(sessionId.toString());
        assertThat(all.toString()).doesNotContain("secretHash", "publicCredentialId", "agentCredentialId", "fingerprint");
        assertSafe(allResult);

        MvcResult limitedResult = mockMvc.perform(get(base + "/activity?limit=2")
                .with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        JsonNode limited = objectMapper.readTree(limitedResult.getResponse().getContentAsString());
        Instant first = Instant.parse(limited.get(0).get("occurredAt").asText());
        Instant second = Instant.parse(limited.get(1).get("occurredAt").asText());
        assertThat(first).isAfterOrEqualTo(second);
        mockMvc.perform(get(base + "/activity?limit=101").with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(base + "/sessions?size=101").with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isBadRequest());

        AgentSession persisted = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(persisted.getAgent().getId()).isEqualTo(agent.getId());
        assertThat(persisted.getAgentCredential().getId()).isEqualTo(credential.id());
        MvcResult sessionsAfterCredentialRevoke = mockMvc.perform(get(base + "/sessions")
                .with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn();
        assertSafe(sessionsAfterCredentialRevoke);
    }

    private void assertAllEndpoints(String base, User user, int expectedStatus) throws Exception {
        var request = user(new No8doUserDetails(user));
        mockMvc.perform(get(base + "/overview").with(request)).andExpect(status().is(expectedStatus));
        mockMvc.perform(get(base + "/sessions").with(request)).andExpect(status().is(expectedStatus));
        mockMvc.perform(get(base + "/activity").with(request)).andExpect(status().is(expectedStatus));
    }

    private void assertAllAnonymous(String base) throws Exception {
        mockMvc.perform(get(base + "/overview")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(base + "/sessions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(base + "/activity")).andExpect(status().isUnauthorized());
    }

    private void assertSafe(MvcResult... results) throws Exception {
        for (MvcResult result : results) {
            JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
            assertSafeNode(root);
        }
    }

    private static void assertSafeNode(JsonNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                assertThat(FORBIDDEN_FIELDS).doesNotContain(field.getKey().toLowerCase());
                assertSafeNode(field.getValue());
            });
        } else if (node.isArray()) {
            node.forEach(AgentHubControllerIntegrationTests::assertSafeNode);
        } else if (node.isTextual()) {
            String value = node.asText().toLowerCase();
            assertThat(value).doesNotContain("secret", "publiccredentialid", "agentcredentialid", "fingerprint", "authorization");
        }
    }

    private Fixture fixture(String prefix) {
        User owner = newUser(prefix + "-owner");
        UUID workspaceId = workspace(prefix + " workspace");
        return new Fixture(owner, workspaceId);
    }

    private User newUser(String prefix) {
        String suffix = UUID.randomUUID().toString();
        User user = userRepository.saveAndFlush(new User(prefix, prefix + "-" + suffix + "@example.test", "hash"));
        userIds.add(user.getId());
        return user;
    }

    private UUID workspace(String name) {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace(name + " " + UUID.randomUUID()));
        workspaceIds.add(workspace.getId());
        return workspace.getId();
    }

    private void member(UUID workspaceId, User user, WorkspaceRole role) {
        memberRepository.saveAndFlush(new WorkspaceMember(workspaceRepository.findById(workspaceId).orElseThrow(), user, role));
    }

    private Agent agent(UUID workspaceId, User actor, String name) {
        return agentRegistryService.createAgent(workspaceId, actor.getId(), name, null, null);
    }

    private UUID register(Fixture fixture, Agent agent, AgentCredentialIssueResponse credential, String suffix) {
        String fingerprint = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        AgentSessionRegistrationRequest request = new AgentSessionRegistrationRequest("Codex " + suffix, "1",
                fixture.workspaceId(), AgentTransport.MCP, fingerprint);
        return sessionRegistry.register(fixture.owner().getId(), request, credential.credential()).sessionId();
    }

    private void setTimes(UUID sessionId, Instant registeredAt, Instant lastSeenAt, Instant lastActivityAt) {
        jdbcTemplate.update("update agent_sessions set registered_at = ?, last_seen_at = ?, last_activity_at = ? where id = ?",
                Timestamp.from(registeredAt), Timestamp.from(lastSeenAt),
                lastActivityAt == null ? null : Timestamp.from(lastActivityAt), sessionId);
    }

    private static String base(UUID workspaceId, UUID agentId) {
        return ROOT.formatted(workspaceId, agentId);
    }

    private record Fixture(User owner, UUID workspaceId) {}
}
