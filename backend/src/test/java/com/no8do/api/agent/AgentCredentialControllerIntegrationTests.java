package com.no8do.api.agent;

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
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.util.ArrayList;
import java.util.List;
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
class AgentCredentialControllerIntegrationTests {

    private static final String CREDENTIALS = "/api/workspaces/%s/agents/%s/credentials";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AgentRegistryService registryService;
    @Autowired private AgentCredentialService credentialService;
    @Autowired private AgentCredentialRepository credentialRepository;
    @Autowired private AgentRepository agentRepository;
    @Autowired private AgentRegistryAuditEntryRepository auditRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

    @Test
    void ownerAndAdminCanCreateListRevokeAndRotateWithSafeMetadataDtos() throws Exception {
        Fixture owner = workspaceActor("credential-api-owner", WorkspaceRole.OWNER);
        User admin = addMember(owner.workspace(), "credential-api-admin", WorkspaceRole.ADMIN);
        Agent agent = createAgent(owner, "API credentials");

        JsonNode ownerCreated = objectMapper.readTree(create(owner.workspace().getId(), agent.getId(), owner.actor())
                .andExpect(status().isCreated()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString());
        assertIssueResponse(ownerCreated);
        UUID ownerCredentialId = UUID.fromString(ownerCreated.get("id").asText());
        String ownerCredential = ownerCreated.get("credential").asText();

        JsonNode adminCreated = objectMapper.readTree(create(owner.workspace().getId(), agent.getId(), admin)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertIssueResponse(adminCreated);
        UUID adminCredentialId = UUID.fromString(adminCreated.get("id").asText());

        JsonNode listed = objectMapper.readTree(list(owner.workspace().getId(), agent.getId(), owner.actor())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(listed.size()).isEqualTo(2);
        listed.forEach(item -> {
            List<String> fields = new ArrayList<>();
            item.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrder("id", "publicCredentialId", "status", "createdAt", "revokedAt");
        });
        assertThat(listed.toString().contains(ownerCredential)).isFalse();
        assertThat(listed.toString()).doesNotContain("secretHash", "secret", "credential");

        JsonNode rotated = objectMapper.readTree(rotate(owner.workspace().getId(), agent.getId(), ownerCredentialId,
                owner.actor()).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString());
        assertIssueResponse(rotated);
        assertThat(rotated.get("id").asText()).isNotEqualTo(ownerCredentialId.toString());
        assertThat(rotated.get("publicCredentialId").asText())
                .isNotEqualTo(ownerCreated.get("publicCredentialId").asText());
        assertThat(rotated.get("credential").asText().equals(ownerCredential)).isFalse();
        assertThat(credentialRepository.findById(ownerCredentialId).orElseThrow().getStatus())
                .isEqualTo(AgentCredentialStatus.REVOKED);

        JsonNode revoked = objectMapper.readTree(revoke(owner.workspace().getId(), agent.getId(), adminCredentialId, admin)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(revoked.get("status").asText()).isEqualTo("REVOKED");
        assertThat(revoked.get("revokedAt").isNull()).isFalse();
        List<AgentRegistryAuditEntry> entries = auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agent.getId());
        assertThat(entries).extracting(AgentRegistryAuditEntry::getEventType)
                .contains(AgentRegistryAuditEventType.AGENT_CREDENTIAL_CREATED,
                        AgentRegistryAuditEventType.AGENT_CREDENTIAL_ROTATED,
                        AgentRegistryAuditEventType.AGENT_CREDENTIAL_REVOKED);
        assertThat(entries.stream().map(entry -> entry.getMetadata().toString())
                .anyMatch(metadata -> metadata.contains(ownerCredential))).isFalse();
        assertThat(entries.stream().map(entry -> entry.getMetadata().toString())
                .anyMatch(metadata -> metadata.contains("secretHash"))).isFalse();
    }

    @Test
    void memberViewerAndOutsiderAreDeniedForEveryCredentialOperation() throws Exception {
        Fixture owner = workspaceActor("credential-api-rbac", WorkspaceRole.OWNER);
        Agent agent = createAgent(owner, "RBAC target");
        AgentCredentialIssueResponse credential = credentialService.create(owner.workspace().getId(), agent.getId(),
                owner.actor().getId());
        User member = addMember(owner.workspace(), "credential-api-member", WorkspaceRole.MEMBER);
        User viewer = addMember(owner.workspace(), "credential-api-viewer", WorkspaceRole.VIEWER);
        User outsider = createUser("credential-api-outsider");

        for (User denied : List.of(member, viewer, outsider)) {
            list(owner.workspace().getId(), agent.getId(), denied).andExpect(status().isForbidden());
            create(owner.workspace().getId(), agent.getId(), denied).andExpect(status().isForbidden());
            revoke(owner.workspace().getId(), agent.getId(), credential.id(), denied).andExpect(status().isForbidden());
            rotate(owner.workspace().getId(), agent.getId(), credential.id(), denied).andExpect(status().isForbidden());
        }
        assertThat(credentialRepository.findById(credential.id()).orElseThrow().getStatus())
                .isEqualTo(AgentCredentialStatus.ACTIVE);
        assertThat(auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agent.getId()).stream()
                .filter(entry -> entry.getEventType().name().startsWith("AGENT_CREDENTIAL_")).count()).isEqualTo(1);
    }

    @Test
    void credentialOperationsAreWorkspaceAndAgentScoped() throws Exception {
        Fixture workspaceA = workspaceActor("credential-api-scope-a", WorkspaceRole.OWNER);
        Agent agentA = createAgent(workspaceA, "Private A");
        AgentCredentialIssueResponse credentialA = credentialService.create(workspaceA.workspace().getId(), agentA.getId(),
                workspaceA.actor().getId());
        Fixture workspaceB = workspaceActor("credential-api-scope-b", WorkspaceRole.ADMIN);
        Agent agentB = createAgent(workspaceB, "Private B");

        JsonNode listB = objectMapper.readTree(list(workspaceB.workspace().getId(), agentB.getId(), workspaceB.actor())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(listB).isEmpty();
        list(workspaceB.workspace().getId(), agentA.getId(), workspaceB.actor())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Agent not found"));
        revoke(workspaceB.workspace().getId(), agentB.getId(), credentialA.id(), workspaceB.actor())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Agent credential not found"));
        rotate(workspaceB.workspace().getId(), agentB.getId(), credentialA.id(), workspaceB.actor())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Agent credential not found"));

        assertThat(credentialRepository.findById(credentialA.id()).orElseThrow().getStatus())
                .isEqualTo(AgentCredentialStatus.ACTIVE);
        assertThat(agentRepository.findById(agentA.getId()).orElseThrow().getWorkspace().getId())
                .isEqualTo(workspaceA.workspace().getId());
    }

    @Test
    void allOperationsRequireHumanAuthenticationAndMutationsRequireCsrf() throws Exception {
        Fixture owner = workspaceActor("credential-api-auth", WorkspaceRole.OWNER);
        Agent agent = createAgent(owner, "Auth target");
        AgentCredentialIssueResponse credential = credentialService.create(owner.workspace().getId(), agent.getId(),
                owner.actor().getId());
        String path = path(owner.workspace().getId(), agent.getId());

        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(path).with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(post(path + "/" + credential.id() + "/revoke").with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(path + "/" + credential.id() + "/rotate").with(csrf()))
                .andExpect(status().isUnauthorized());

        No8doUserDetails principal = new No8doUserDetails(owner.actor());
        mockMvc.perform(post(path).with(user(principal))).andExpect(status().isForbidden());
        mockMvc.perform(post(path + "/" + credential.id() + "/revoke").with(user(principal)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path + "/" + credential.id() + "/rotate").with(user(principal)))
                .andExpect(status().isForbidden());

        assertThat(credentialRepository.findById(credential.id()).orElseThrow().getStatus())
                .isEqualTo(AgentCredentialStatus.ACTIVE);
    }

    private org.springframework.test.web.servlet.ResultActions list(UUID workspaceId, UUID agentId, User actor)
            throws Exception {
        return mockMvc.perform(get(path(workspaceId, agentId)).with(user(new No8doUserDetails(actor))));
    }

    private org.springframework.test.web.servlet.ResultActions create(UUID workspaceId, UUID agentId, User actor)
            throws Exception {
        return mockMvc.perform(post(path(workspaceId, agentId)).with(user(new No8doUserDetails(actor))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON));
    }

    private org.springframework.test.web.servlet.ResultActions revoke(UUID workspaceId, UUID agentId,
            UUID credentialId, User actor) throws Exception {
        return mockMvc.perform(post(path(workspaceId, agentId) + "/" + credentialId + "/revoke")
                .with(user(new No8doUserDetails(actor))).with(csrf()));
    }

    private org.springframework.test.web.servlet.ResultActions rotate(UUID workspaceId, UUID agentId,
            UUID credentialId, User actor) throws Exception {
        return mockMvc.perform(post(path(workspaceId, agentId) + "/" + credentialId + "/rotate")
                .with(user(new No8doUserDetails(actor))).with(csrf()));
    }

    private Fixture workspaceActor(String prefix, WorkspaceRole role) {
        User actor = createUser(prefix);
        Workspace workspace = workspaceRepository.save(new Workspace(prefix + " " + UUID.randomUUID()));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, actor, role));
        return new Fixture(actor, workspace);
    }

    private User addMember(Workspace workspace, String prefix, WorkspaceRole role) {
        User user = createUser(prefix);
        workspaceMemberRepository.save(new WorkspaceMember(workspace, user, role));
        return user;
    }

    private User createUser(String prefix) {
        String suffix = UUID.randomUUID().toString();
        return userRepository.save(new User(prefix + "-" + suffix, prefix + "-" + suffix + "@example.test", "hash"));
    }

    private Agent createAgent(Fixture fixture, String name) {
        return registryService.createAgent(fixture.workspace().getId(), fixture.actor().getId(), name, null, null);
    }

    private static String path(UUID workspaceId, UUID agentId) {
        return CREDENTIALS.formatted(workspaceId, agentId);
    }

    private static void assertIssueResponse(JsonNode response) {
        List<String> fields = new ArrayList<>();
        response.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "publicCredentialId", "status", "createdAt", "credential");
        assertThat(response.get("status").asText()).isEqualTo("ACTIVE");
        String serialized = response.toString();
        assertThat(serialized).doesNotContain("secretHash", "password", "token", "fingerprint", "session");
    }

    private record Fixture(User actor, Workspace workspace) {}
}
