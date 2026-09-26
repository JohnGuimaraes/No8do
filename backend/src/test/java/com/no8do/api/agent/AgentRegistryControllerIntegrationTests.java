package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentRegistryControllerIntegrationTests {

    private static final String COLLECTION = "/api/workspaces/%s/agents";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AgentRegistryService registryService;
    @Autowired private AgentRepository agentRepository;
    @Autowired private AgentRegistryAuditEntryRepository auditRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

    @Test
    void ownerAndAdminCanCreateListDetailAndUpdateWithSafeDtosAndRealRegistryAudit() throws Exception {
        Fixture owner = workspaceActor("registry-api-owner", WorkspaceRole.OWNER);
        User admin = addMember(owner.workspace(), "registry-api-admin", WorkspaceRole.ADMIN);
        Fixture otherWorkspace = workspaceActor("registry-api-other", WorkspaceRole.OWNER);
        Agent otherAgent = registryService.createAgent(otherWorkspace.workspace().getId(),
                otherWorkspace.actor().getId(), "Isolated", null, null);
        UUID callerSuppliedId = UUID.randomUUID();
        String forcedCreatedAt = "2000-01-01T00:00:00Z";

        MvcResult ownerCreate = create(owner.workspace().getId(), owner.actor(), Map.of(
                "name", "  Owner Agent  ",
                "description", "Initial description",
                "providerDescriptor", "custom-provider",
                "id", callerSuppliedId,
                "workspaceId", otherWorkspace.workspace().getId(),
                "lifecycleStatus", "ARCHIVED",
                "createdByUser", Map.of("id", otherWorkspace.actor().getId(), "email", otherWorkspace.actor().getEmail()),
                "createdAt", forcedCreatedAt,
                "updatedAt", forcedCreatedAt))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode ownerPayload = objectMapper.readTree(ownerCreate.getResponse().getContentAsString());
        assertSafeResponse(ownerPayload);
        UUID ownerAgentId = UUID.fromString(ownerPayload.get("id").asText());
        assertThat(ownerAgentId).isNotEqualTo(callerSuppliedId);
        assertThat(ownerPayload.get("workspaceId").asText()).isEqualTo(owner.workspace().getId().toString());
        assertThat(ownerPayload.get("name").asText()).isEqualTo("Owner Agent");
        assertThat(ownerPayload.get("lifecycleStatus").asText()).isEqualTo("ACTIVE");
        assertThat(ownerPayload.get("createdAt").asText()).isNotEqualTo(forcedCreatedAt);
        assertThat(ownerCreate.getResponse().getContentAsString()).doesNotContain(otherWorkspace.actor().getEmail());

        JsonNode adminPayload = objectMapper.readTree(create(owner.workspace().getId(), admin,
                Map.of("name", "Admin Agent", "description", "Admin notes", "providerDescriptor", "runner-v1"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertSafeResponse(adminPayload);
        UUID adminAgentId = UUID.fromString(adminPayload.get("id").asText());

        for (User reader : List.of(owner.actor(), admin)) {
            JsonNode listed = objectMapper.readTree(list(owner.workspace().getId(), reader)
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            List<String> listedIds = StreamSupport.stream(listed.spliterator(), false)
                    .map(item -> item.get("id").asText()).toList();
            assertThat(listedIds).containsExactlyInAnyOrder(ownerAgentId.toString(), adminAgentId.toString())
                    .doesNotContain(otherAgent.getId().toString());
            listed.forEach(AgentRegistryControllerIntegrationTests::assertSafeResponse);
            assertSafeResponse(objectMapper.readTree(detail(owner.workspace().getId(), ownerAgentId, reader)
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()));
        }

        MvcResult updated = update(owner.workspace().getId(), ownerAgentId, admin, Map.of(
                "name", "Updated by Admin",
                "description", "Revised description",
                "providerDescriptor", "runner-v2",
                "id", callerSuppliedId,
                "workspaceId", otherWorkspace.workspace().getId(),
                "lifecycleStatus", "ARCHIVED",
                "createdByUserId", otherWorkspace.actor().getId(),
                "createdAt", forcedCreatedAt,
                "updatedAt", forcedCreatedAt))
                .andExpect(status().isOk()).andReturn();
        JsonNode updatedPayload = objectMapper.readTree(updated.getResponse().getContentAsString());
        assertSafeResponse(updatedPayload);
        assertThat(updatedPayload.get("id").asText()).isEqualTo(ownerAgentId.toString());
        assertThat(updatedPayload.get("workspaceId").asText()).isEqualTo(owner.workspace().getId().toString());
        assertThat(updatedPayload.get("lifecycleStatus").asText()).isEqualTo("ACTIVE");
        assertThat(updatedPayload.get("name").asText()).isEqualTo("Updated by Admin");
        assertThat(updatedPayload.get("description").asText()).isEqualTo("Revised description");
        assertThat(updatedPayload.get("providerDescriptor").asText()).isEqualTo("runner-v2");

        Agent persisted = agentRepository.findById(ownerAgentId).orElseThrow();
        assertThat(persisted.getWorkspace().getId()).isEqualTo(owner.workspace().getId());
        assertThat(persisted.getCreatedByUser().getId()).isEqualTo(owner.actor().getId());
        assertThat(persisted.getLifecycleStatus()).isEqualTo(AgentLifecycleStatus.ACTIVE);
        List<AgentRegistryAuditEntry> entries = auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(ownerAgentId);
        assertThat(entries).extracting(AgentRegistryAuditEntry::getEventType)
                .containsExactly(AgentRegistryAuditEventType.AGENT_CREATED, AgentRegistryAuditEventType.AGENT_UPDATED);
        assertThat(entries.get(0).getActorUserId()).isEqualTo(owner.actor().getId());
        assertThat(entries.get(1).getActorUserId()).isEqualTo(admin.getId());
        assertThat(entries.get(1).getMetadata().toString()).contains("name", "description", "providerDescriptor")
                .doesNotContain("Updated by Admin", "Revised description", "runner-v2");

        update(owner.workspace().getId(), adminAgentId, owner.actor(), Map.of(
                "name", "Updated by Owner", "description", "Owner notes", "providerDescriptor", "runner-owner"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated by Owner"));
        assertThat(auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(adminAgentId).getLast().getActorUserId())
                .isEqualTo(owner.actor().getId());
    }

    @Test
    void memberViewerAndOutsiderAreDeniedOnEveryAdministrativeRoute() throws Exception {
        Fixture owner = workspaceActor("registry-api-rbac-owner", WorkspaceRole.OWNER);
        Agent agent = registryService.createAgent(owner.workspace().getId(), owner.actor().getId(), "Protected", null, null);
        User member = addMember(owner.workspace(), "registry-api-member", WorkspaceRole.MEMBER);
        User viewer = addMember(owner.workspace(), "registry-api-viewer", WorkspaceRole.VIEWER);
        User outsider = createUser("registry-api-outsider");

        for (User denied : List.of(member, viewer, outsider)) {
            list(owner.workspace().getId(), denied).andExpect(status().isForbidden());
            detail(owner.workspace().getId(), agent.getId(), denied).andExpect(status().isForbidden());
            create(owner.workspace().getId(), denied, Map.of("name", "Denied create"))
                    .andExpect(status().isForbidden());
            update(owner.workspace().getId(), agent.getId(), denied, Map.of("name", "Denied update"))
                    .andExpect(status().isForbidden());
            changeLifecycle(owner.workspace().getId(), agent.getId(), denied, "DISABLED")
                    .andExpect(status().isForbidden());
        }

        assertThat(agentRepository.findById(agent.getId()).orElseThrow().getName()).isEqualTo("Protected");
        assertThat(agentRepository.findById(agent.getId()).orElseThrow().getLifecycleStatus())
                .isEqualTo(AgentLifecycleStatus.ACTIVE);
        assertThat(auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agent.getId())).hasSize(1);
    }

    @Test
    void crossWorkspaceAgentIsIndistinguishableFromMissingAndNeverMutated() throws Exception {
        Fixture workspaceA = workspaceActor("registry-api-isolation-a", WorkspaceRole.OWNER);
        Agent agentA = registryService.createAgent(workspaceA.workspace().getId(), workspaceA.actor().getId(),
                "Private to A", "Original", "provider-a");
        Fixture adminB = workspaceActor("registry-api-isolation-b", WorkspaceRole.ADMIN);

        list(adminB.workspace().getId(), adminB.actor()).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("[]"));
        MvcResult crossWorkspaceDetail = detail(adminB.workspace().getId(), agentA.getId(), adminB.actor())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Agent not found")).andReturn();
        MvcResult missingDetail = detail(adminB.workspace().getId(), UUID.randomUUID(), adminB.actor())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Agent not found")).andReturn();
        assertThat(crossWorkspaceDetail.getResponse().getContentAsString())
                .isEqualTo(missingDetail.getResponse().getContentAsString());
        update(adminB.workspace().getId(), agentA.getId(), adminB.actor(), Map.of("name", "Cross-workspace attack"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Agent not found"));
        changeLifecycle(adminB.workspace().getId(), agentA.getId(), adminB.actor(), "ARCHIVED")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Agent not found"));

        Agent unchanged = agentRepository.findById(agentA.getId()).orElseThrow();
        assertThat(unchanged.getWorkspace().getId()).isEqualTo(workspaceA.workspace().getId());
        assertThat(unchanged.getName()).isEqualTo("Private to A");
        assertThat(unchanged.getDescription()).isEqualTo("Original");
        assertThat(unchanged.getProviderDescriptor()).isEqualTo("provider-a");
        assertThat(unchanged.getLifecycleStatus()).isEqualTo(AgentLifecycleStatus.ACTIVE);
        assertThat(auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agentA.getId())).hasSize(1);
    }

    @Test
    void lifecycleDelegatesTransitionsToRegistryServiceAndAuditsEachChange() throws Exception {
        Fixture owner = workspaceActor("registry-api-lifecycle-owner", WorkspaceRole.OWNER);
        User admin = addMember(owner.workspace(), "registry-api-lifecycle-admin", WorkspaceRole.ADMIN);
        Agent agent = registryService.createAgent(owner.workspace().getId(), owner.actor().getId(), "Lifecycle", null, null);

        changeLifecycle(owner.workspace().getId(), agent.getId(), owner.actor(), "DISABLED")
                .andExpect(status().isOk()).andExpect(jsonPath("$.lifecycleStatus").value("DISABLED"));
        changeLifecycle(owner.workspace().getId(), agent.getId(), admin, "ACTIVE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"));
        changeLifecycle(owner.workspace().getId(), agent.getId(), owner.actor(), "ARCHIVED")
                .andExpect(status().isOk()).andExpect(jsonPath("$.lifecycleStatus").value("ARCHIVED"));
        changeLifecycle(owner.workspace().getId(), agent.getId(), admin, "ACTIVE")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Agent lifecycle transition is not allowed"));

        List<AgentRegistryAuditEntry> entries = auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agent.getId());
        assertThat(entries).extracting(AgentRegistryAuditEntry::getEventType).containsExactly(
                AgentRegistryAuditEventType.AGENT_CREATED,
                AgentRegistryAuditEventType.AGENT_LIFECYCLE_CHANGED,
                AgentRegistryAuditEventType.AGENT_LIFECYCLE_CHANGED,
                AgentRegistryAuditEventType.AGENT_LIFECYCLE_CHANGED);
        assertThat(entries).extracting(AgentRegistryAuditEntry::getActorUserId)
                .containsExactly(owner.actor().getId(), owner.actor().getId(), admin.getId(), owner.actor().getId());
        assertThat(agentRepository.findById(agent.getId()).orElseThrow().getLifecycleStatus())
                .isEqualTo(AgentLifecycleStatus.ARCHIVED);
    }

    @Test
    void patchWithOmittedOptionalFieldsClearsThemAndAuditsTheChangedDetails() throws Exception {
        Fixture owner = workspaceActor("registry-api-patch-semantics", WorkspaceRole.OWNER);
        Agent agent = registryService.createAgent(owner.workspace().getId(), owner.actor().getId(),
                "Before", "Description", "provider-v1");

        JsonNode response = objectMapper.readTree(update(owner.workspace().getId(), agent.getId(), owner.actor(),
                Map.of("name", "After"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertSafeResponse(response);
        assertThat(response.get("name").asText()).isEqualTo("After");
        assertThat(response.get("description").isNull()).isTrue();
        assertThat(response.get("providerDescriptor").isNull()).isTrue();
        List<AgentRegistryAuditEntry> entries = auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agent.getId());
        assertThat(entries).hasSize(2);
        assertThat(entries.getLast().getEventType()).isEqualTo(AgentRegistryAuditEventType.AGENT_UPDATED);
        assertThat(entries.getLast().getActorUserId()).isEqualTo(owner.actor().getId());
        assertThat(entries.getLast().getMetadata().toString()).contains("name", "description", "providerDescriptor");
    }

    @Test
    void validationRejectsMissingBlankAndOversizedNamesAndKeepsTextFieldsUnbounded() throws Exception {
        Fixture owner = workspaceActor("registry-api-validation", WorkspaceRole.OWNER);
        long agentsBefore = agentRepository.count();
        long auditBefore = auditRepository.count();

        create(owner.workspace().getId(), owner.actor(), Map.of("description", "missing name"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Invalid request"));
        create(owner.workspace().getId(), owner.actor(), Map.of("name", "  "))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Invalid request"));
        create(owner.workspace().getId(), owner.actor(), Map.of("name", "n".repeat(161)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Invalid request"));
        create(owner.workspace().getId(), owner.actor(), Map.of("name", "n".repeat(160)))
                .andExpect(status().isCreated());
        create(owner.workspace().getId(), owner.actor(), Map.of("name", "Valid", "description", "d".repeat(4096),
                "providerDescriptor", "p".repeat(4096))).andExpect(status().isCreated());

        assertThat(agentRepository.count()).isEqualTo(agentsBefore + 2);
        assertThat(auditRepository.count()).isEqualTo(auditBefore + 2);
    }

    @Test
    void apiRequiresAuthenticationAndReturnsConsistentBadRequestForUnreadableLifecycle() throws Exception {
        Fixture owner = workspaceActor("registry-api-auth", WorkspaceRole.OWNER);
        Agent agent = registryService.createAgent(owner.workspace().getId(), owner.actor().getId(), "Auth", null, null);
        String collection = collection(owner.workspace().getId());
        String item = collection + "/" + agent.getId();

        mockMvc.perform(get(collection)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(item)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(collection).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Anonymous\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(item).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Anonymous\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(item + "/lifecycle").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"NOT_A_STATUS\"}")).andExpect(status().isUnauthorized());

        changeLifecycle(owner.workspace().getId(), agent.getId(), owner.actor(), "NOT_A_STATUS")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Invalid request"));
        changeLifecycle(owner.workspace().getId(), agent.getId(), owner.actor(), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Invalid request"));

        mockMvc.perform(patch(item + "/lifecycle").with(user(new No8doUserDetails(owner.actor())))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Invalid request"));
    }

    @Test
    void authenticatedMutationsWithoutCsrfAreRejected() throws Exception {
        Fixture owner = workspaceActor("registry-api-csrf", WorkspaceRole.OWNER);
        Agent agent = registryService.createAgent(owner.workspace().getId(), owner.actor().getId(), "CSRF", null, null);
        String collection = collection(owner.workspace().getId());
        String item = collection + "/" + agent.getId();
        No8doUserDetails principal = new No8doUserDetails(owner.actor());

        mockMvc.perform(post(collection).with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"No token\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(item).with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"No token\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(item + "/lifecycle").with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isForbidden());

        assertThat(agentRepository.findById(agent.getId()).orElseThrow().getName()).isEqualTo("CSRF");
        assertThat(agentRepository.findById(agent.getId()).orElseThrow().getLifecycleStatus())
                .isEqualTo(AgentLifecycleStatus.ACTIVE);
        assertThat(auditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agent.getId())).hasSize(1);
    }

    private ResultActions list(UUID workspaceId, User actor) throws Exception {
        return mockMvc.perform(get(collection(workspaceId)).with(user(new No8doUserDetails(actor))));
    }

    private ResultActions detail(UUID workspaceId, UUID agentId, User actor) throws Exception {
        return mockMvc.perform(get(collection(workspaceId) + "/" + agentId)
                .with(user(new No8doUserDetails(actor))));
    }

    private ResultActions create(UUID workspaceId, User actor, Map<String, ?> body) throws Exception {
        return mockMvc.perform(post(collection(workspaceId)).with(user(new No8doUserDetails(actor))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions update(UUID workspaceId, UUID agentId, User actor, Map<String, ?> body) throws Exception {
        return mockMvc.perform(patch(collection(workspaceId) + "/" + agentId)
                .with(user(new No8doUserDetails(actor))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions changeLifecycle(UUID workspaceId, UUID agentId, User actor, String lifecycle) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", lifecycle);
        return mockMvc.perform(patch(collection(workspaceId) + "/" + agentId + "/lifecycle")
                .with(user(new No8doUserDetails(actor))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
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

    private static String collection(UUID workspaceId) {
        return COLLECTION.formatted(workspaceId);
    }

    private static void assertSafeResponse(JsonNode response) {
        List<String> fields = new java.util.ArrayList<>();
        response.fieldNames().forEachRemaining(fields::add);
        assertThat(fields)
                .containsExactlyInAnyOrder("id", "workspaceId", "name", "description", "providerDescriptor",
                        "lifecycleStatus", "createdAt", "updatedAt");
        assertThat(response.toString()).doesNotContainIgnoringCase("password", "passwordHash", "pat", "token",
                "secret", "fingerprint", "transport", "createdByUser", "audit", "session", "actorUserId");
    }

    private record Fixture(User actor, Workspace workspace) {}
}
