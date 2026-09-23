package com.no8do.api.agent;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
                        "\"transport\":\"MCP\",\"protocolName\":\"spoofed\",\"protocolVersion\":99,\"registeredAt\":\"2000-01-01T00:00:00Z\",")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientName").value("Codex Desktop"))
                .andExpect(jsonPath("$.clientVersion").value("9.8"))
                .andExpect(jsonPath("$.workspaceId").doesNotExist())
                .andExpect(jsonPath("$.transport").value("MCP"))
                .andExpect(jsonPath("$.protocolName").value(protocolProvider.current().protocolName()))
                .andExpect(jsonPath("$.protocolVersion").value(protocolProvider.current().protocolVersion()))
                .andExpect(jsonPath("$.registeredAt").isNotEmpty())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID sessionId = UUID.fromString(response.get("sessionId").asText());
        AgentSession session = sessionRepository.findById(sessionId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(session.getUserId()).isEqualTo(user.getId());
        org.assertj.core.api.Assertions.assertThat(session.getWorkspaceId()).isNull();
        org.assertj.core.api.Assertions.assertThat(response.get("registeredAt").asText()).isNotEqualTo("2000-01-01T00:00:00Z");
        org.assertj.core.api.Assertions.assertThat(response.has("transportSessionFingerprint")).isFalse();
        org.assertj.core.api.Assertions.assertThat(response.has("userId")).isFalse();
        org.assertj.core.api.Assertions.assertThat(session.getTransportSessionFingerprint()).isEqualTo(FINGERPRINT);
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
