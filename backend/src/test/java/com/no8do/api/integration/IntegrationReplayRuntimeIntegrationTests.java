package com.no8do.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.agent.*;
import com.no8do.api.replay.*;
import com.no8do.api.user.*;
import com.no8do.api.workspace.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.junit.jupiter.api.AfterEach;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IntegrationReplayRuntimeIntegrationTests {
    private static final String ROOT = "/api/integration-runtime/replays";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired WorkspaceMemberRepository members;
    @Autowired AgentRegistryService agents;
    @Autowired IntegrationAuthorizationRepository authorizations;
    @Autowired IntegrationCredentialCodec codec;
    @Autowired AgentSessionRepository sessions;
    @Autowired ReplayService replays;
    @Autowired ReplayRelationService relations;
    @Autowired IntegrationBootstrapService bootstrap;
    @Autowired AgentCapabilityGrantService grants;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @MockitoSpyBean AgentPolicyEngine policyEngine;
    @AfterEach void resetPolicy() { org.mockito.Mockito.reset(policyEngine); }

    @Test void registersDerivedIdentityWithoutHumanOrLegacyCredential() throws Exception {
        Fixture f = fixture();
        AgentSession session = sessions.findById(f.session()).orElseThrow();
        assertThat(session.getUserId()).isNull();
        assertThat(session.getAgentCredential()).isNull();
        assertThat(session.getWorkspaceId()).isEqualTo(f.workspace().getId());
        assertThat(session.getAgent().getId()).isEqualTo(f.agent().getId());
        assertThat(session.getIntegrationAuthorization().getId()).isEqualTo(f.authorization().getId());
        assertThat(read(f, ROOT)).doesNotContain(f.credential());
        mvc.perform(post("/api/agent-sessions").header("Authorization", "Bearer " + f.credential())
                .header("X-No8do-Agent-Credential", "not-a-real-secret")
                .contentType(MediaType.APPLICATION_JSON).content(registration(null)))
                .andExpect(status().isBadRequest());
        Fixture other = fixture();
        mvc.perform(post("/api/agent-sessions").header("Authorization", "Bearer " + f.credential())
                .contentType(MediaType.APPLICATION_JSON).content(registration(other.workspace().getId())))
                .andExpect(status().isForbidden());
    }

    @Test void allReadOperationsAreScopedAndHumanEndpointsRemainDenied() throws Exception {
        Fixture a = fixture(), b = fixture();
        UUID first = replay(a, "visible knowledge"), target = replay(a, "related knowledge");
        UUID foreign = replay(b, "FOREIGN_PRIVATE");
        relations.create(a.workspace().getId(), first, a.user().getId(),
                new CreateReplayRelationRequest(target, ReplayRelationType.RELATED_TO));
        assertThat(read(a, ROOT)).contains("visible knowledge").doesNotContain("FOREIGN_PRIVATE");
        assertThat(read(a, ROOT + "/search?q=visible")).contains(first.toString()).doesNotContain(foreign.toString());
        String similar = mvc.perform(post(ROOT + "/similar").header("Authorization", "Bearer " + a.credential())
                .header("X-No8do-Agent-Session-Id", a.session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"visible\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(similar).contains(first.toString()).doesNotContain(foreign.toString());
        for (String suffix : List.of("", "/quality", "/versions", "/versions/1", "/relations")) {
            read(a, ROOT + "/" + first + suffix);
            mvc.perform(get(ROOT + "/" + foreign + suffix).header("Authorization", "Bearer " + a.credential())
                    .header("X-No8do-Agent-Session-Id", a.session())).andExpect(status().isNotFound());
        }
        String human = "/api/workspaces/" + a.workspace().getId() + "/replays";
        mvc.perform(get(human).header("Authorization", "Bearer " + a.credential()))
                .andExpect(status().isForbidden());
        mvc.perform(get(ROOT).header("Authorization", "Bearer " + a.credential()))
                .andExpect(status().isBadRequest());
        mvc.perform(get(ROOT).header("Authorization", "Bearer " + a.credential())
                .header("X-No8do-Agent-Session-Id", b.session())).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "expired", "revoked", "disabled", "archived", "grantor-disabled", "grantor-member", "session-revoked", "session-disconnected"})
    void invalidationDeniesRuntimeWithoutSilentFallback(String state) throws Exception {
        Fixture f = fixture();
        String token = f.credential();
        switch (state) {
            case "invalid" -> token = "no8do_int_invalid";
            case "expired" -> jdbc.update("update integration_authorizations set expires_at = ? where id = ?",
                    java.sql.Timestamp.from(Instant.now().minusSeconds(1)), f.authorization().getId());
            case "revoked" -> bootstrap.revoke(f.authorization().getId(), f.user().getId());
            case "disabled" -> agents.changeLifecycle(f.workspace().getId(), f.agent().getId(), f.user().getId(), AgentLifecycleStatus.DISABLED);
            case "archived" -> agents.changeLifecycle(f.workspace().getId(), f.agent().getId(), f.user().getId(), AgentLifecycleStatus.ARCHIVED);
            case "grantor-disabled" -> jdbc.update("update users set enabled = false where id = ?", f.user().getId());
            case "grantor-member" -> jdbc.update("update workspace_members set role = 'MEMBER' where workspace_id = ? and user_id = ?", f.workspace().getId(), f.user().getId());
            case "session-revoked" -> jdbc.update("update agent_sessions set revoked_at = ?, revoked_by_user_id = ? where id = ?",
                    java.sql.Timestamp.from(Instant.now()), f.user().getId(), f.session());
            case "session-disconnected" -> jdbc.update("update agent_sessions set disconnected_at = ? where id = ?", java.sql.Timestamp.from(Instant.now()), f.session());
            default -> throw new AssertionError(state);
        }
        em.clear();
        var result = mvc.perform(get(ROOT).header("Authorization", "Bearer " + token)
                .header("X-No8do-Agent-Session-Id", f.session())).andReturn();
        assertThat(result.getResponse().getStatus()).isIn(401, 403, 409);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(token);
        if (List.of("expired", "disabled", "grantor-disabled", "grantor-member").contains(state)) {
            assertThat(jdbc.queryForObject("select status from integration_authorizations where id = ?", String.class,
                    f.authorization().getId())).isEqualTo("ACTIVE");
            assertThat(jdbc.queryForObject("select revoked_at from agent_sessions where id = ?", java.sql.Timestamp.class,
                    f.session())).isNull();
        }
    }

    @Test void missingCapabilityAndRuntimeOffDeny() throws Exception {
        Fixture f = fixture();
        grants.revoke(f.workspace().getId(), f.agent().getId(), AgentCapability.REPLAY_CATALOG_LIST, f.user().getId());
        mvc.perform(get(ROOT).header("Authorization", "Bearer " + f.credential())
                .header("X-No8do-Agent-Session-Id", f.session())).andExpect(status().isForbidden());
        jdbc.update("update agent_sessions set runtime_mode = 'OFF' where id = ?", f.session());
        em.clear();
        mvc.perform(get(ROOT + "/search?q=test").header("Authorization", "Bearer " + f.credential())
                .header("X-No8do-Agent-Session-Id", f.session())).andExpect(status().isForbidden());
    }

    @Test void enforcedPolicyDenialIsNotBypassedByIntegration() throws Exception {
        Fixture f = fixture();
        org.mockito.Mockito.doReturn(List.of(new AgentPolicyDecision("workspace-isolation-required",
                AgentPolicyDecisionType.DENY, "test denial"))).when(policyEngine)
                .evaluate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        mvc.perform(get(ROOT).header("Authorization", "Bearer " + f.credential())
                .header("X-No8do-Agent-Session-Id", f.session())).andExpect(status().isForbidden());
    }

    @Test void discoveryPolicyDenialBlocksSimilarEvenWhenSearchIsAllowed() throws Exception {
        Fixture f = fixture();
        org.mockito.Mockito.doAnswer(invocation -> {
            AgentPolicyContext context = invocation.getArgument(1);
            if (context.operation() == AgentCapability.REUSABLE_KNOWLEDGE_DISCOVERY) {
                return List.of(new AgentPolicyDecision("workspace-isolation-required",
                        AgentPolicyDecisionType.DENY, "test discovery denial"));
            }
            return invocation.callRealMethod();
        }).when(policyEngine).evaluate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        mvc.perform(post(ROOT + "/similar").header("Authorization", "Bearer " + f.credential())
                .header("X-No8do-Agent-Session-Id", f.session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"knowledge\"}")).andExpect(status().isForbidden());
        for (AgentCapability capability : List.of(AgentCapability.REPLAY_SEARCH,
                AgentCapability.REUSABLE_KNOWLEDGE_DISCOVERY)) {
            org.mockito.Mockito.verify(policyEngine).evaluate(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.argThat(context -> context.operation() == capability
                            && context.requestedWorkspaceId().equals(f.workspace().getId())
                            && context.session().getId().equals(f.session())));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/11111111-1111-4111-8111-111111111111", "/11111111-1111-4111-8111-111111111111/usages", "/11111111-1111-4111-8111-111111111111/relations"})
    void replayMutationsAreNotAllowlisted(String suffix) throws Exception {
        Fixture f = fixture();
        for (var request : List.of(post(ROOT + suffix), patch(ROOT + suffix), delete(ROOT + suffix))) {
            mvc.perform(request.header("Authorization", "Bearer " + f.credential())
                    .header("X-No8do-Agent-Session-Id", f.session()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    private String read(Fixture f, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + f.credential())
                .header("X-No8do-Agent-Session-Id", f.session())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
    private UUID replay(Fixture f, String title) {
        return replays.create(f.workspace().getId(), f.user().getId(), new CreateReplayRequest(title,
                ReplayType.REFERENCE, null, "safe solution", null, List.of(), List.of(), ReplayStatus.DRAFT, null)).id();
    }
    private String registration(UUID workspace) throws Exception {
        return mapper.writeValueAsString(new AgentSessionRegistrationRequest("test", "1", workspace, AgentTransport.MCP,
                UUID.randomUUID().toString().replace("-", "").repeat(2)));
    }
    private Fixture fixture() throws Exception {
        String unique = UUID.randomUUID().toString();
        User user = users.saveAndFlush(new User("runtime" + unique, unique + "@example.test", "hash"));
        Workspace workspace = workspaces.saveAndFlush(new Workspace("runtime" + unique));
        members.saveAndFlush(new WorkspaceMember(workspace, user, WorkspaceRole.OWNER));
        Agent agent = agents.createAgent(workspace.getId(), user.getId(), "runtime", null, null);
        var issued = codec.issue(); Instant now = Instant.now().minusSeconds(3600);
        var authorization = authorizations.saveAndFlush(new IntegrationAuthorization(UUID.randomUUID(), issued.selector(),
                issued.tokenHash(), agent, user.getId(), UUID.randomUUID(), IntegrationHostType.CODEX, "test", "1", now, now.plusSeconds(7200)));
        String body = mvc.perform(post("/api/agent-sessions").header("Authorization", "Bearer " + issued.serialized())
                .contentType(MediaType.APPLICATION_JSON).content(registration(null))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID session = UUID.fromString(mapper.readTree(body).get("sessionId").asText());
        return new Fixture(user, workspace, agent, authorization, issued.serialized(), session);
    }
    private record Fixture(User user, Workspace workspace, Agent agent, IntegrationAuthorization authorization,
            String credential, UUID session) {}
}
