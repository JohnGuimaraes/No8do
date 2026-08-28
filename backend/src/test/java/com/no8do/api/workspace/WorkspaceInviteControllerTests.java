package com.no8do.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "no8do.registration.enabled=false")
@AutoConfigureMockMvc
@Transactional
class WorkspaceInviteControllerTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private WorkspaceInviteRepository inviteRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void ownerAndAdminCanCreateInvitesButViewerMemberAndOutsiderCannot() throws Exception {
        User owner = createUser("owner");
        User admin = createUser("admin");
        User viewer = createUser("viewer");
        User member = createUser("member");
        User outsider = createUser("outsider");
        Workspace workspace = workspace(owner, WorkspaceRole.OWNER);
        memberRepository.save(new WorkspaceMember(workspace, admin, WorkspaceRole.ADMIN));
        memberRepository.save(new WorkspaceMember(workspace, viewer, WorkspaceRole.VIEWER));
        memberRepository.save(new WorkspaceMember(workspace, member, WorkspaceRole.MEMBER));

        createInvite(workspace, owner, " OWNER@EXAMPLE.COM ", "ADMIN")
            .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("owner@example.com"));
        createInvite(workspace, admin, "viewer@example.com", "VIEWER").andExpect(status().isOk());
        createInvite(workspace, viewer, "blocked@example.com", "VIEWER").andExpect(status().isForbidden());
        createInvite(workspace, member, "blocked@example.com", "VIEWER").andExpect(status().isForbidden());
        createInvite(workspace, outsider, "blocked@example.com", "VIEWER").andExpect(status().isForbidden());
        createInvite(workspace, owner, "blocked@example.com", "OWNER").andExpect(status().isBadRequest());
    }

    @Test
    void invitationResponseDoesNotExposeHashAndAcceptsOnlyMatchingAccountOnce() throws Exception {
        User owner = createUser("owner");
        User invited = createUser("invited");
        User different = createUser("different");
        Workspace workspace = workspace(owner, WorkspaceRole.OWNER);
        String token = createToken(workspace, owner, invited.getEmail(), "VIEWER");

        mockMvc.perform(get("/api/workspace-invites/{token}", token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(invited.getEmail()))
            .andExpect(jsonPath("$.tokenHash").doesNotExist());
        mockMvc.perform(post("/api/workspace-invites/{token}/accept", token)
                .with(user(new No8doUserDetails(different))).with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/workspace-invites/{token}/accept", token)
                .with(user(new No8doUserDetails(invited))).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("VIEWER"));
        assertThat(memberRepository.findByWorkspaceIdAndUserId(workspace.getId(), invited.getId())).isPresent();
        assertThat(inviteRepository.findByTokenHash(hash(token)).orElseThrow().getTokenHash()).isNotEqualTo(token);
        mockMvc.perform(post("/api/workspace-invites/{token}/accept", token)
                .with(user(new No8doUserDetails(invited))).with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidExpiredAndRevokedInvitesAreRejected() throws Exception {
        User owner = createUser("owner");
        Workspace workspace = workspace(owner, WorkspaceRole.OWNER);
        assertThat(mockMvc.perform(get("/api/workspace-invites/not-a-token")).andReturn().getResponse().getStatus()).isEqualTo(400);

        WorkspaceInvite expired = inviteRepository.save(new WorkspaceInvite(workspace, "expired@example.com", WorkspaceInviteRole.VIEWER, hash("expired"), owner, Instant.now().minusSeconds(1)));
        WorkspaceInvite revoked = inviteRepository.save(new WorkspaceInvite(workspace, "revoked@example.com", WorkspaceInviteRole.ADMIN, hash("revoked"), owner, Instant.now().plusSeconds(3600)));
        revoked.revoke(Instant.now());
        mockMvc.perform(get("/api/workspace-invites/{token}", "expired")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/workspace-invites/{token}", "revoked")).andExpect(status().isBadRequest());
        assertThat(expired.getTokenHash()).isNotBlank();
    }

    @Test
    void inviteRegistrationUsesInviteEmailAndBcryptEvenWhenPublicRegistrationIsClosed() throws Exception {
        User owner = createUser("owner");
        Workspace workspace = workspace(owner, WorkspaceRole.OWNER);
        String email = "invite-register-" + UUID.randomUUID() + "@example.com";
        String token = createToken(workspace, owner, email, "ADMIN");

        mockMvc.perform(post("/api/workspace-invites/{token}/register", token).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("name", "Invite User", "password", "password123"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value(email))
            .andExpect(jsonPath("$.invitation.role").value("ADMIN"));
        User registered = userRepository.findByEmail(email).orElseThrow();
        assertThat(passwordEncoder.matches("password123", registered.getPasswordHash())).isTrue();
        assertThat(memberRepository.findByWorkspaceIdAndUserId(workspace.getId(), registered.getId())).isPresent();
        assertThat(inviteRepository.findByTokenHash(hash(token)).orElseThrow().getAcceptedAt()).isNotNull();
    }

    private org.springframework.test.web.servlet.ResultActions createInvite(Workspace workspace, User user, String email, String role) throws Exception {
        return mockMvc.perform(post("/api/workspaces/{workspaceId}/invites", workspace.getId())
            .with(user(new No8doUserDetails(user))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("email", email, "role", role))));
    }

    private String createToken(Workspace workspace, User user, String email, String role) throws Exception {
        MvcResult result = createInvite(workspace, user, email, role).andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String inviteUrl = body.path("inviteUrl").asText();
        return inviteUrl.substring(inviteUrl.indexOf("token=") + "token=".length());
    }

    private Workspace workspace(User owner, WorkspaceRole role) {
        Workspace workspace = workspaceRepository.save(new Workspace("Workspace " + UUID.randomUUID()));
        memberRepository.save(new WorkspaceMember(workspace, owner, role));
        return workspace;
    }

    private User createUser(String prefix) {
        return userRepository.save(new User(prefix + " " + UUID.randomUUID(), prefix + "-" + UUID.randomUUID() + "@example.com", "hash"));
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
