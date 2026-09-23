package com.no8do.api.agent;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentSessionControllerTests {
    private static final String FINGERPRINT = "a".repeat(64);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private AgentSessionRepository sessionRepository;
    @Autowired private No8doAgentProtocolProvider protocolProvider;

    @Test
    void registrationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/agent-sessions").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(request(null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedPrincipalOwnsRegistrationAndResponseOmitsFingerprint() throws Exception {
        User user = userRepository.save(new User("session-user", "session-user@example.test", "hash"));
        MvcResult result = mockMvc.perform(post("/api/agent-sessions").with(user(new No8doUserDetails(user))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(UUID.randomUUID().toString()).replace("\"transport\":\"MCP\",",
                        "\"transport\":\"MCP\",\"runtimeMode\":\"OFF\",\"protocolName\":\"spoofed\",\"protocolVersion\":99,\"registeredAt\":\"2000-01-01T00:00:00Z\",")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientName").value("Codex Desktop"))
                .andExpect(jsonPath("$.clientVersion").value("9.8"))
                .andExpect(jsonPath("$.workspaceId").doesNotExist())
                .andExpect(jsonPath("$.transport").value("MCP"))
                .andExpect(jsonPath("$.runtimeMode").value("FULL"))
                .andExpect(jsonPath("$.protocolName").value(protocolProvider.current().protocolName()))
                .andExpect(jsonPath("$.protocolVersion").value(protocolProvider.current().protocolVersion()))
                .andExpect(jsonPath("$.registeredAt").isNotEmpty())
                .andExpect(jsonPath("$.lastActivityAt").doesNotExist())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID sessionId = UUID.fromString(response.get("sessionId").asText());
        AgentSession session = sessionRepository.findById(sessionId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(session.getUserId()).isEqualTo(user.getId());
        org.assertj.core.api.Assertions.assertThat(session.getWorkspaceId()).isNull();
        org.assertj.core.api.Assertions.assertThat(session.getLastSeenAt()).isEqualTo(session.getRegisteredAt());
        org.assertj.core.api.Assertions.assertThat(session.getLastActivityAt()).isNull();
        org.assertj.core.api.Assertions.assertThat(session.getDisconnectedAt()).isNull();
        org.assertj.core.api.Assertions.assertThat(response.get("registeredAt").asText()).isNotEqualTo("2000-01-01T00:00:00Z");
        org.assertj.core.api.Assertions.assertThat(response.has("transportSessionFingerprint")).isFalse();
        org.assertj.core.api.Assertions.assertThat(response.has("userId")).isFalse();
        org.assertj.core.api.Assertions.assertThat(session.getTransportSessionFingerprint()).isEqualTo(FINGERPRINT);
        org.assertj.core.api.Assertions.assertThat(session.getRuntimeMode()).isEqualTo(AgentRuntimeMode.FULL);
    }

    @Test
    void heartbeatUsesServerTimeChangesOnlyLastSeenAndRequiresSessionOwnership() throws Exception {
        User owner = userRepository.save(new User("heartbeat-owner", "heartbeat-owner@example.test", "hash"));
        User other = userRepository.save(new User("heartbeat-other", "heartbeat-other@example.test", "hash"));
        String sessionId = register(owner, "e".repeat(64));
        AgentSession before = sessionRepository.findById(UUID.fromString(sessionId)).orElseThrow();
        String clientTimestamp = "2000-01-01T00:00:00Z";

        mockMvc.perform(post("/api/agent-sessions/{sessionId}/heartbeat", sessionId)
                .with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lastSeenAt\":\"" + clientTimestamp + "\",\"lastActivityAt\":\"" + clientTimestamp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.lastSeenAt").isNotEmpty())
                .andExpect(jsonPath("$.lastSeenAt").value(org.hamcrest.Matchers.not(clientTimestamp)));

        AgentSession after = sessionRepository.findById(UUID.fromString(sessionId)).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(after.getLastSeenAt()).isAfterOrEqualTo(before.getLastSeenAt());
        org.assertj.core.api.Assertions.assertThat(after.getLastActivityAt()).isNull();
        org.assertj.core.api.Assertions.assertThat(after.getRuntimeMode()).isEqualTo(AgentRuntimeMode.FULL);
        mockMvc.perform(post("/api/agent-sessions/{sessionId}/heartbeat", sessionId).with(user(new No8doUserDetails(other))).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/agent-sessions/{sessionId}/heartbeat", sessionId).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedAgentSessionRequestsTouchActivityButHeartbeatDoesNotAndHeaderlessRequestDoesNotTouch() throws Exception {
        User owner = userRepository.save(new User("activity-owner", "activity-owner@example.test", "hash"));
        String sessionId = register(owner, "f".repeat(64));
        UUID id = UUID.fromString(sessionId);

        mockMvc.perform(get("/api/agent-protocol").header(AgentSessionContextInterceptor.HEADER_NAME, sessionId)
                .with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk());
        AgentSession active = sessionRepository.findById(id).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(active.getLastActivityAt()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(active.getLastSeenAt()).isEqualTo(active.getLastActivityAt());

        mockMvc.perform(get("/api/agent-sessions/{sessionId}/context", sessionId)
                .header(AgentSessionContextInterceptor.HEADER_NAME, sessionId)
                .with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presenceStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.lastActivityAt").isNotEmpty())
                .andExpect(jsonPath("$.lastSeenAt").isNotEmpty());
        active = sessionRepository.findById(id).orElseThrow();

        mockMvc.perform(post("/api/agent-sessions/{sessionId}/heartbeat", sessionId)
                .with(user(new No8doUserDetails(owner))).with(csrf()))
                .andExpect(status().isOk());
        AgentSession afterHeartbeat = sessionRepository.findById(id).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(afterHeartbeat.getLastActivityAt()).isEqualTo(active.getLastActivityAt());

        mockMvc.perform(get("/api/agent-protocol").with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk());
        AgentSession withoutSessionHeader = sessionRepository.findById(id).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(withoutSessionHeader.getLastSeenAt()).isEqualTo(afterHeartbeat.getLastSeenAt());
        org.assertj.core.api.Assertions.assertThat(withoutSessionHeader.getLastActivityAt()).isEqualTo(afterHeartbeat.getLastActivityAt());
    }

    @Test
    void contextIsOwnerScopedAndRuntimeModeChangesOnlyTheSelectedSession() throws Exception {
        User owner = userRepository.save(new User("runtime-owner", "runtime-owner@example.test", "hash"));
        User other = userRepository.save(new User("runtime-other", "runtime-other@example.test", "hash"));
        String firstId = register(owner, "b".repeat(64));
        String secondId = register(owner, "c".repeat(64));

        mockMvc.perform(get("/api/agent-sessions/{sessionId}/context", firstId)
                .with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(firstId))
                .andExpect(jsonPath("$.runtimeMode").value("FULL"))
                .andExpect(jsonPath("$.effectiveCapabilities").isArray())
                .andExpect(jsonPath("$.effectiveCapabilities.length()").value(15))
                .andExpect(jsonPath("$.policies[?(@.id == 'workspace-isolation-required')].enforcement").value("ENFORCED"))
                .andExpect(jsonPath("$.policies[?(@.id == 'secrets-forbidden')].enforcement").value("ADVISORY"))
                .andExpect(jsonPath("$.presenceStatus").value("CONNECTED"))
                .andExpect(jsonPath("$.lastSeenAt").isNotEmpty())
                .andExpect(jsonPath("$.lastActivityAt").doesNotExist())
                .andExpect(jsonPath("$.disconnectedAt").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(patch("/api/agent-sessions/{sessionId}/runtime-mode", firstId)
                .with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"runtimeMode\":\"RETRIEVAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runtimeMode").value("RETRIEVAL"))
                .andExpect(jsonPath("$.effectiveCapabilities[?(@.id == 'REPLAY_SEARCH')]").exists())
                .andExpect(jsonPath("$.effectiveCapabilities[?(@.id == 'REPLAY_CREATE')]").doesNotExist());

        mockMvc.perform(get("/api/agent-sessions/{sessionId}/context", firstId)
                .with(user(new No8doUserDetails(other))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/agent-sessions/{sessionId}/runtime-mode", firstId)
                .with(user(new No8doUserDetails(other))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"runtimeMode\":\"OFF\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/agent-sessions/{sessionId}/context", secondId)
                .with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.runtimeMode").value("FULL"));
    }

    @Test
    void sessionHeaderRequiresTheSameAuthenticatedOwnerAndCannotReplaceAuthentication() throws Exception {
        User owner = userRepository.save(new User("header-owner", "header-owner@example.test", "hash"));
        User other = userRepository.save(new User("header-other", "header-other@example.test", "hash"));
        String sessionId = register(owner, "d".repeat(64));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays", UUID.randomUUID())
                .header(AgentSessionContextInterceptor.HEADER_NAME, sessionId)
                .with(user(new No8doUserDetails(other))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Agent session access denied"));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays", UUID.randomUUID())
                .header(AgentSessionContextInterceptor.HEADER_NAME, sessionId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disconnectIsOwnerScopedIdempotentTerminalAndPreservesContextAndNormalRequests() throws Exception {
        User owner = userRepository.save(new User("disconnect-owner", "disconnect-owner@example.test", "hash"));
        User other = userRepository.save(new User("disconnect-other", "disconnect-other@example.test", "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Disconnected session scope"));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.MEMBER));
        String sessionId = register(owner, "9".repeat(64));
        UUID id = UUID.fromString(sessionId);
        AgentSession before = sessionRepository.findById(id).orElseThrow();
        String forgedClientTimestamp = "2000-01-01T00:00:00Z";

        mockMvc.perform(post("/api/agent-sessions/{sessionId}/disconnect", sessionId)
                .with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"disconnectedAt\":\"" + forgedClientTimestamp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presenceStatus").value("DISCONNECTED"))
                .andExpect(jsonPath("$.disconnectedAt").isNotEmpty())
                .andExpect(jsonPath("$.disconnectedAt").value(org.hamcrest.Matchers.not(forgedClientTimestamp)));
        AgentSession disconnected = sessionRepository.findById(id).orElseThrow();
        java.time.Instant firstDisconnectedAt = disconnected.getDisconnectedAt();
        java.time.Instant lastSeenBeforeClosedHeartBeat = disconnected.getLastSeenAt();
        org.assertj.core.api.Assertions.assertThat(firstDisconnectedAt).isNotNull().isAfterOrEqualTo(before.getRegisteredAt());

        mockMvc.perform(post("/api/agent-sessions/{sessionId}/disconnect", sessionId)
                .with(user(new No8doUserDetails(owner))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presenceStatus").value("DISCONNECTED"))
                .andExpect(jsonPath("$.disconnectedAt").value(firstDisconnectedAt.toString()));
        org.assertj.core.api.Assertions.assertThat(sessionRepository.findById(id).orElseThrow().getDisconnectedAt())
                .isEqualTo(firstDisconnectedAt);

        mockMvc.perform(post("/api/agent-sessions/{sessionId}/disconnect", sessionId)
                .with(user(new No8doUserDetails(other))).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/agent-sessions/{sessionId}/disconnect", sessionId).with(csrf()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/agent-sessions/{sessionId}/heartbeat", sessionId)
                .with(user(new No8doUserDetails(owner))).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("AGENT_SESSION_DISCONNECTED"));
        org.assertj.core.api.Assertions.assertThat(sessionRepository.findById(id).orElseThrow().getLastSeenAt())
                .isEqualTo(lastSeenBeforeClosedHeartBeat);
        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays", workspace.getId())
                .header(AgentSessionContextInterceptor.HEADER_NAME, sessionId)
                .with(user(new No8doUserDetails(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("AGENT_SESSION_DISCONNECTED"));
        mockMvc.perform(get("/api/agent-sessions/{sessionId}/context", sessionId)
                .header(AgentSessionContextInterceptor.HEADER_NAME, sessionId)
                .with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presenceStatus").value("DISCONNECTED"))
                .andExpect(jsonPath("$.disconnectedAt").value(firstDisconnectedAt.toString()));
        org.assertj.core.api.Assertions.assertThat(sessionRepository.findById(id).orElseThrow().getLastActivityAt()).isNull();

        mockMvc.perform(get("/api/workspaces/{workspaceId}/replays", workspace.getId())
                .with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(sessionRepository.findById(id).orElseThrow().getDisconnectedAt())
                .isEqualTo(firstDisconnectedAt);
    }

    private String register(User owner, String fingerprint) throws Exception {
        String request = request(null).replace(FINGERPRINT, fingerprint);
        MvcResult result = mockMvc.perform(post("/api/agent-sessions").with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("sessionId").asText();
    }

    @Test
    void workspaceMembershipIsValidatedAndFingerprintIsIdempotentButImmutable() throws Exception {
        User user = userRepository.save(new User("workspace-agent", "workspace-agent@example.test", "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Agent sessions"));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, user, WorkspaceRole.MEMBER));
        String request = request(null).replace("\"workspaceId\":null", "\"workspaceId\":\"" + workspace.getId() + "\"");
        MvcResult first = mockMvc.perform(post("/api/agent-sessions").with(user(new No8doUserDetails(user))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated()).andReturn();
        String sessionId = objectMapper.readTree(first.getResponse().getContentAsString()).get("sessionId").asText();
        mockMvc.perform(post("/api/agent-sessions").with(user(new No8doUserDetails(user))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(sessionId));

        User other = userRepository.save(new User("other-agent", "other-agent@example.test", "hash"));
        mockMvc.perform(post("/api/agent-sessions").with(user(new No8doUserDetails(other))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isForbidden());
        String incompatibleClient = request.replace("Codex Desktop", "Different Client");
        mockMvc.perform(post("/api/agent-sessions").with(user(new No8doUserDetails(user))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(incompatibleClient)).andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingOrMalformedFingerprintAndWorkspaceWithoutMembership() throws Exception {
        User user = userRepository.save(new User("denied-agent", "denied-agent@example.test", "hash"));
        mockMvc.perform(post("/api/agent-sessions").with(user(new No8doUserDetails(user))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(request(null).replace(FINGERPRINT, "bad")))
                .andExpect(status().isBadRequest());
        Workspace workspace = workspaceRepository.save(new Workspace("Private workspace"));
        String request = request(null).replace("\"workspaceId\":null", "\"workspaceId\":\"" + workspace.getId() + "\"");
        mockMvc.perform(post("/api/agent-sessions").with(user(new No8doUserDetails(user))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isForbidden());
    }

    private static String request(String userId) {
        return "{\"clientName\":\"Codex Desktop\",\"clientVersion\":\"9.8\",\"workspaceId\":null,"
                + "\"transport\":\"MCP\",\"transportSessionFingerprint\":\"" + FINGERPRINT + "\""
                + (userId == null ? "" : ",\"userId\":\"" + userId + "\"") + "}";
    }
}
