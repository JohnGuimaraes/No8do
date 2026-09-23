package com.no8do.api.agent;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentSessionDiscoveryControllerTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private AgentSessionRepository sessionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void listIsOwnerScopedPagedOrderedAndDoesNotExposeSecrets() throws Exception {
        User owner = createUser("discovery-owner");
        User other = createUser("discovery-other");
        UUID first = register(owner, "a".repeat(64), null, "Desktop", "9.8");
        UUID second = register(owner, "b".repeat(64), null, "Desktop", "9.8");
        UUID third = register(owner, "c".repeat(64), null, "Desktop", "9.8");
        register(other, "d".repeat(64), null, "Desktop", "9.8");

        mockMvc.perform(get("/api/agent-sessions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/agent-sessions/{id}", first)).andExpect(status().isUnauthorized());

        Instant sameTime = Instant.parse("2026-09-23T10:00:00Z");
        jdbcTemplate.update("update agent_sessions set registered_at = ? where user_id = ?",
                Timestamp.from(sameTime), owner.getId());
        jdbcTemplate.update("update agent_sessions set registered_at = ? where id = ?",
                Timestamp.from(sameTime.plusSeconds(1)), first);
        entityManager.clear();

        MvcResult firstPage = mockMvc.perform(get("/api/agent-sessions?page=0&size=2")
                .with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].sessionId").value(first.toString()))
                .andExpect(jsonPath("$.content[1].sessionId").value(max(second, third).toString()))
                .andExpect(jsonPath("$.content[0].presenceStatus").value("CONNECTED"))
                .andExpect(jsonPath("$.content[0].protocolName").exists())
                .andExpect(jsonPath("$.content[0].lastSeenAt").exists())
                .andExpect(jsonPath("$.content[0].disconnectedAt").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();
        JsonNode content = objectMapper.readTree(firstPage.getResponse().getContentAsString()).get("content");
        org.assertj.core.api.Assertions.assertThat(content.get(0).get("sessionId").asText())
                .isNotEqualTo(content.get(1).get("sessionId").asText());
        org.assertj.core.api.Assertions.assertThat(content.get(0).has("transportSessionFingerprint")).isFalse();
        org.assertj.core.api.Assertions.assertThat(content.get(0).has("userId")).isFalse();
        org.assertj.core.api.Assertions.assertThat(content.get(0).has("rawMcpSessionId")).isFalse();

        mockMvc.perform(get("/api/agent-sessions?page=1&size=2").with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/agent-sessions/{id}", first).with(user(new No8doUserDetails(other))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/agent-sessions/{id}", first).with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value(first.toString()))
                .andExpect(jsonPath("$.clientVersion").value("9.8"));
        mockMvc.perform(get("/api/agent-sessions?page=-1").with(user(new No8doUserDetails(owner))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/agent-sessions?size=101").with(user(new No8doUserDetails(owner))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void workspaceRuntimeAndClientFiltersAreAppliedAndWorkspaceAccessIsValidated() throws Exception {
        User owner = createUser("filter-owner");
        User outsider = createUser("filter-outsider");
        Workspace workspace = workspaceRepository.save(new Workspace("Discovery workspace"));
        Workspace deniedWorkspace = workspaceRepository.save(new Workspace("Denied discovery workspace"));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.MEMBER));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, outsider, WorkspaceRole.MEMBER));
        register(owner, "e".repeat(64), workspace.getId(), "Codex Desktop", "1.0");
        UUID matching = register(owner, "f".repeat(64), workspace.getId(), "Codex CLI", "2.0");
        UUID colleagueSession = register(outsider, "7".repeat(64), workspace.getId(), "Codex CLI", "2.0");
        jdbcTemplate.update("update agent_sessions set runtime_mode = 'RETRIEVAL' where id = ?", matching);
        entityManager.clear();
        register(owner, "1".repeat(64), null, "Codex CLI", "2.0");

        mockMvc.perform(get("/api/agent-sessions?workspaceId={workspaceId}&runtimeMode=RETRIEVAL&clientName=cli",
                workspace.getId()).with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sessionId").value(matching.toString()))
                .andExpect(jsonPath("$.content[0].runtimeMode").value("RETRIEVAL"));
        mockMvc.perform(get("/api/agent-sessions?workspaceId={workspaceId}", deniedWorkspace.getId())
                .with(user(new No8doUserDetails(owner))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/agent-sessions?workspaceId={workspaceId}", workspace.getId())
                .with(user(new No8doUserDetails(outsider))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sessionId").value(colleagueSession.toString()));
        mockMvc.perform(get("/api/agent-sessions?clientName=cli").with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void presenceIsDerivedForEachResponseIncludingTimeoutAndExplicitDisconnect() throws Exception {
        User owner = createUser("presence-discovery-owner");
        UUID connected = register(owner, "2".repeat(64), null, "Presence", "1");
        UUID active = register(owner, "3".repeat(64), null, "Presence", "1");
        UUID idle = register(owner, "4".repeat(64), null, "Presence", "1");
        UUID timedOut = register(owner, "5".repeat(64), null, "Presence", "1");
        UUID disconnected = register(owner, "6".repeat(64), null, "Presence", "1");
        Instant now = Instant.now();
        jdbcTemplate.update("update agent_sessions set last_seen_at = ?, last_activity_at = null where id = ?",
                Timestamp.from(now), connected);
        jdbcTemplate.update("update agent_sessions set last_seen_at = ?, last_activity_at = ? where id = ?",
                Timestamp.from(now), Timestamp.from(now), active);
        jdbcTemplate.update("update agent_sessions set last_seen_at = ?, last_activity_at = ? where id = ?",
                Timestamp.from(now), Timestamp.from(now.minusSeconds(180)), idle);
        jdbcTemplate.update("update agent_sessions set last_seen_at = ?, last_activity_at = null where id = ?",
                Timestamp.from(now.minusSeconds(360)), timedOut);
        entityManager.clear();
        mockMvc.perform(post("/api/agent-sessions/{sessionId}/disconnect", disconnected)
                .with(user(new No8doUserDetails(owner))).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/agent-sessions?clientName=Presence&size=10")
                .with(user(new No8doUserDetails(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.sessionId == '" + connected + "')].presenceStatus").value("CONNECTED"))
                .andExpect(jsonPath("$.content[?(@.sessionId == '" + active + "')].presenceStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.content[?(@.sessionId == '" + idle + "')].presenceStatus").value("IDLE"))
                .andExpect(jsonPath("$.content[?(@.sessionId == '" + timedOut + "')].presenceStatus").value("DISCONNECTED"))
                .andExpect(jsonPath("$.content[?(@.sessionId == '" + disconnected + "')].presenceStatus").value("DISCONNECTED"));
    }

    private User createUser(String username) {
        return userRepository.save(new User(username, username + "@example.test", "hash"));
    }

    private UUID register(User owner, String fingerprint, UUID workspaceId, String clientName, String clientVersion)
            throws Exception {
        String workspace = workspaceId == null ? "null" : "\"" + workspaceId + "\"";
        String request = "{\"clientName\":\"" + clientName + "\",\"clientVersion\":\"" + clientVersion
                + "\",\"workspaceId\":" + workspace + ",\"transport\":\"MCP\",\"transportSessionFingerprint\":\""
                + fingerprint + "\"}";
        MvcResult result = mockMvc.perform(post("/api/agent-sessions").with(user(new No8doUserDetails(owner))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("sessionId").asText());
    }

    private static UUID max(UUID... values) {
        return java.util.Arrays.stream(values)
                .max(java.util.Comparator.comparing(UUID::toString)).orElseThrow();
    }
}
