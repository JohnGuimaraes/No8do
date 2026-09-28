package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.project.ProjectService;
import com.no8do.api.project.ProjectStatus;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.DeleteWorkspaceRequest;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import com.no8do.api.workspace.WorkspaceService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class AgentProjectAssignmentControllerIntegrationTests {
    private static final String ROOT = "/api/workspaces/%s/agents/%s/projects";
    private static final Set<String> FORBIDDEN_FIELDS = Set.of("secret", "secrethash", "credential",
            "publiccredentialid", "agentcredentialid", "pat", "token", "fingerprint", "authorization",
            "headers", "agentsession", "connectionsecret");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private AgentRepository agentRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private AgentProjectAssignmentRepository assignmentRepository;
    @Autowired private AgentRegistryAuditEntryRepository registryAuditRepository;
    @Autowired private AgentRegistryService agentRegistryService;
    @Autowired private AgentProjectAssignmentService assignmentService;
    @Autowired private ProjectService projectService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<UUID> workspaceIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> agentIds = new ArrayList<>();
    private final List<UUID> projectIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (UUID projectId : projectIds) projectRepository.deleteById(projectId);
            for (UUID agentId : agentIds) agentRepository.deleteById(agentId);
            for (UUID workspaceId : workspaceIds) {
                memberRepository.deleteByWorkspaceId(workspaceId);
                workspaceRepository.deleteById(workspaceId);
            }
            for (UUID userId : userIds) userRepository.deleteById(userId);
        });
        workspaceIds.clear();
        userIds.clear();
        agentIds.clear();
        projectIds.clear();
    }

    @Test
    void ownerAndAdminCanListAssignAndUnassignWithNoFalseAuditForNoOps() throws Exception {
        Fixture fixture = fixture("assignment-rbac");
        User admin = newUser("assignment-admin");
        User member = newUser("assignment-member");
        User viewer = newUser("assignment-viewer");
        User outsider = newUser("assignment-outsider");
        addMember(fixture.workspaceId(), admin, WorkspaceRole.ADMIN);
        addMember(fixture.workspaceId(), member, WorkspaceRole.MEMBER);
        addMember(fixture.workspaceId(), viewer, WorkspaceRole.VIEWER);
        Project project = project(fixture.workspaceId(), fixture.owner(), "Visible project");
        String path = path(fixture.workspaceId(), fixture.agent().getId());

        assertCanManage(path, fixture.owner(), project.getId());
        assertCanManage(path, admin, project.getId());
        assertDenied(path, member, project.getId());
        assertDenied(path, viewer, project.getId());
        assertDenied(path, outsider, project.getId());
        assertAnonymousDenied(path, project.getId());

        mockMvc.perform(get(path).with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        Instant before = Instant.now();
        assignmentService.assign(fixture.workspaceId(), fixture.agent().getId(), project.getId(), admin.getId());
        Instant after = Instant.now();
        AgentProjectAssignment assignment = assignmentRepository
                .findByAgent_IdAndProject_Id(fixture.agent().getId(), project.getId()).orElseThrow();
        assertThat(assignment.getAssignedByUserId()).isEqualTo(admin.getId());
        assertThat(assignment.getAssignedAt()).isBetween(before, after);

        MvcResult list = mockMvc.perform(get(path).with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").value(project.getId().toString()))
                .andExpect(jsonPath("$[0].name").value("Visible project"))
                .andExpect(jsonPath("$[0].status").value(ProjectStatus.IDEA.name()))
                .andExpect(jsonPath("$[0].assignedAt").exists())
                .andReturn();
        assertSafe(list);

        long auditBeforeNoOp = registryAuditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(fixture.agent().getId())
                .stream().filter(entry -> entry.getEventType() == AgentRegistryAuditEventType.AGENT_PROJECT_ASSIGNED).count();
        mockMvc.perform(put(path + "/" + project.getId()).with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNoContent());
        long auditAfterNoOp = registryAuditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(fixture.agent().getId())
                .stream().filter(entry -> entry.getEventType() == AgentRegistryAuditEventType.AGENT_PROJECT_ASSIGNED).count();
        assertThat(auditAfterNoOp).isEqualTo(auditBeforeNoOp);

        List<AgentRegistryAuditEntry> assignedAudits = registryAuditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(fixture.agent().getId())
                .stream().filter(entry -> entry.getEventType() == AgentRegistryAuditEventType.AGENT_PROJECT_ASSIGNED)
                .toList();
        AgentRegistryAuditEntry assignedAudit = assignedAudits.stream()
                .filter(entry -> entry.getActorUserId().equals(admin.getId())).findFirst().orElseThrow();
        assertThat(assignedAudit.getWorkspaceId()).isEqualTo(fixture.workspaceId());
        assertThat(assignedAudit.getMetadata().path("agentId").asText()).isEqualTo(fixture.agent().getId().toString());
        assertThat(assignedAudit.getMetadata().path("projectId").asText()).isEqualTo(project.getId().toString());

        long unassignAuditsBeforeDelete = registryAuditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(fixture.agent().getId())
                .stream().filter(entry -> entry.getEventType() == AgentRegistryAuditEventType.AGENT_PROJECT_UNASSIGNED).count();
        mockMvc.perform(delete(path + "/" + project.getId()).with(user(new No8doUserDetails(fixture.owner())))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(path + "/" + project.getId()).with(user(new No8doUserDetails(fixture.owner())))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(assignmentRepository.countByAgent_Id(fixture.agent().getId())).isZero();
        long unassignAudits = registryAuditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(fixture.agent().getId())
                .stream().filter(entry -> entry.getEventType() == AgentRegistryAuditEventType.AGENT_PROJECT_UNASSIGNED).count();
        assertThat(unassignAudits).isEqualTo(unassignAuditsBeforeDelete + 1);
    }

    @Test
    void assignmentsAreManyToManyAndRemovingOnePairLeavesTheOtherPairIntact() {
        Fixture fixture = fixture("assignment-many");
        Project first = project(fixture.workspaceId(), fixture.owner(), "Project one");
        Project shared = project(fixture.workspaceId(), fixture.owner(), "Shared project");
        Agent secondAgent = agentRegistryService.createAgent(fixture.workspaceId(), fixture.owner().getId(),
                "Second agent", null, null);
        agentIds.add(secondAgent.getId());

        assignmentService.assign(fixture.workspaceId(), fixture.agent().getId(), first.getId(), fixture.owner().getId());
        assignmentService.assign(fixture.workspaceId(), fixture.agent().getId(), shared.getId(), fixture.owner().getId());
        assignmentService.assign(fixture.workspaceId(), secondAgent.getId(), shared.getId(), fixture.owner().getId());
        assignmentService.unassign(fixture.workspaceId(), fixture.agent().getId(), shared.getId(), fixture.owner().getId());

        assertThat(assignmentRepository.findProjectSummaries(fixture.workspaceId(), fixture.agent().getId()))
                .extracting(AgentProjectAssignmentResponse::projectId).containsExactly(first.getId());
        assertThat(assignmentRepository.findProjectSummaries(fixture.workspaceId(), secondAgent.getId()))
                .extracting(AgentProjectAssignmentResponse::projectId).containsExactly(shared.getId());
    }

    @Test
    void crossWorkspaceAgentOrProjectIsIndistinguishableFromMissingAndNeverAssigned() throws Exception {
        Fixture fixture = fixture("assignment-tenant-a");
        Fixture other = fixture("assignment-tenant-b");
        Project otherProject = project(other.workspaceId(), other.owner(), "Private other project");
        String path = path(fixture.workspaceId(), fixture.agent().getId());
        String otherPath = path(other.workspaceId(), fixture.agent().getId());

        MvcResult crossWorkspaceProject = mockMvc.perform(put(path + "/" + otherProject.getId())
                        .with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNotFound()).andReturn();
        MvcResult missingProject = mockMvc.perform(put(path + "/" + UUID.randomUUID())
                        .with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNotFound()).andReturn();
        assertThat(crossWorkspaceProject.getResponse().getContentAsString())
                .isEqualTo(missingProject.getResponse().getContentAsString());

        mockMvc.perform(get(otherPath).with(user(new No8doUserDetails(other.owner()))))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(otherPath + "/" + otherProject.getId())
                        .with(user(new No8doUserDetails(other.owner()))).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(otherPath + "/" + otherProject.getId())
                        .with(user(new No8doUserDetails(other.owner()))).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(assignmentRepository.countByAgent_Id(fixture.agent().getId())).isZero();
        assertThat(assignmentRepository.countByAgent_Id(other.agent().getId())).isZero();
    }

    @Test
    void disabledAndArchivedAgentsKeepTheirAssignmentsButArchivedCannotReceiveNewOnes() throws Exception {
        Fixture fixture = fixture("assignment-lifecycle");
        Project assigned = project(fixture.workspaceId(), fixture.owner(), "Assigned");
        Project archivedProject = project(fixture.workspaceId(), fixture.owner(), "Archived project");
        archivedProject.setArchivedAt(Instant.now());
        projectRepository.saveAndFlush(archivedProject);
        assignmentService.assign(fixture.workspaceId(), fixture.agent().getId(), archivedProject.getId(), fixture.owner().getId());

        agentRegistryService.changeLifecycle(fixture.workspaceId(), fixture.agent().getId(), fixture.owner().getId(),
                AgentLifecycleStatus.DISABLED);
        assignmentService.assign(fixture.workspaceId(), fixture.agent().getId(), assigned.getId(), fixture.owner().getId());
        assertThat(assignmentRepository.countByAgent_Id(fixture.agent().getId())).isEqualTo(2);

        agentRegistryService.changeLifecycle(fixture.workspaceId(), fixture.agent().getId(), fixture.owner().getId(),
                AgentLifecycleStatus.ARCHIVED);
        Project rejected = project(fixture.workspaceId(), fixture.owner(), "Rejected for archived Agent");
        mockMvc.perform(put(path(fixture.workspaceId(), fixture.agent().getId()) + "/" + rejected.getId())
                        .with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(assignmentRepository.countByAgent_Id(fixture.agent().getId())).isEqualTo(2);
        assertThat(registryAuditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(fixture.agent().getId()))
                .extracting(AgentRegistryAuditEntry::getEventType)
                .contains(AgentRegistryAuditEventType.AGENT_PROJECT_ASSIGNED);

        MvcResult activity = mockMvc.perform(get("/api/workspaces/%s/agents/%s/activity"
                        .formatted(fixture.workspaceId(), fixture.agent().getId()))
                        .with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk()).andReturn();
        assertThat(activity.getResponse().getContentAsString()).contains("AGENT_PROJECT_ASSIGNED", archivedProject.getId().toString());
        assertSafe(activity);

        mockMvc.perform(delete(path(fixture.workspaceId(), fixture.agent().getId()) + "/" + assigned.getId())
                        .with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(assignmentRepository.countByAgent_Id(fixture.agent().getId())).isEqualTo(1);
    }

    @Test
    void deletingProjectAgentOrWorkspaceCascadesOnlyAssignmentRowsAndPreservesRegistryHistory() {
        Fixture fixture = fixture("assignment-delete");
        Project deletedWithService = project(fixture.workspaceId(), fixture.owner(), "Deleted project");
        assignmentService.assign(fixture.workspaceId(), fixture.agent().getId(), deletedWithService.getId(), fixture.owner().getId());
        projectService.delete(fixture.workspaceId(), deletedWithService.getId(), fixture.owner().getId());
        assertThat(assignmentRepository.countByAgent_Id(fixture.agent().getId())).isZero();
        assertThat(agentRepository.existsById(fixture.agent().getId())).isTrue();
        assertThat(registryAuditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(fixture.agent().getId()))
                .extracting(AgentRegistryAuditEntry::getEventType)
                .contains(AgentRegistryAuditEventType.AGENT_PROJECT_ASSIGNED);

        Project leftBehind = project(fixture.workspaceId(), fixture.owner(), "Agent deletion target");
        assignmentService.assign(fixture.workspaceId(), fixture.agent().getId(), leftBehind.getId(), fixture.owner().getId());
        UUID deletedAgentId = fixture.agent().getId();
        agentRepository.deleteById(deletedAgentId);
        assertThat(assignmentRepository.countByAgent_Id(deletedAgentId)).isZero();
        assertThat(projectRepository.existsById(leftBehind.getId())).isTrue();
        assertThat(registryAuditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(deletedAgentId))
                .extracting(AgentRegistryAuditEntry::getEventType)
                .contains(AgentRegistryAuditEventType.AGENT_PROJECT_ASSIGNED);

        Agent workspaceAgent = agentRegistryService.createAgent(fixture.workspaceId(), fixture.owner().getId(),
                "Workspace delete target", null, null);
        agentIds.add(workspaceAgent.getId());
        Project workspaceProject = project(fixture.workspaceId(), fixture.owner(), "Workspace delete target");
        assignmentService.assign(fixture.workspaceId(), workspaceAgent.getId(), workspaceProject.getId(), fixture.owner().getId());
        UUID workspaceId = fixture.workspaceId();
        UUID projectId = workspaceProject.getId();
        UUID agentId = workspaceAgent.getId();
        String workspaceName = workspaceRepository.findById(workspaceId).orElseThrow().getName();
        workspaceService.delete(workspaceId, fixture.owner().getId(), new DeleteWorkspaceRequest(workspaceName));

        assertThat(workspaceRepository.existsById(workspaceId)).isFalse();
        assertThat(projectRepository.existsById(projectId)).isFalse();
        assertThat(agentRepository.existsById(agentId)).isFalse();
        assertThat(assignmentRepository.countByAgent_Id(agentId)).isZero();
        assertThat(registryAuditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agentId))
                .extracting(AgentRegistryAuditEntry::getEventType)
                .contains(AgentRegistryAuditEventType.AGENT_PROJECT_ASSIGNED);
    }

    @Test
    void deletingAssignedByAccountNullsOnlyOperationalProvenanceAndKeepsHistoricalActor() {
        Fixture fixture = fixture("assignment-provenance");
        User admin = newUser("assignment-provenance-admin");
        addMember(fixture.workspaceId(), admin, WorkspaceRole.ADMIN);
        Project project = project(fixture.workspaceId(), fixture.owner(), "Provenance project");
        assignmentService.assign(fixture.workspaceId(), fixture.agent().getId(), project.getId(), admin.getId());
        UUID assignmentId = assignmentRepository.findByAgent_IdAndProject_Id(fixture.agent().getId(), project.getId())
                .orElseThrow().getId();
        memberRepository.delete(memberRepository.findByWorkspaceIdAndUserId(fixture.workspaceId(), admin.getId()).orElseThrow());
        userRepository.deleteById(admin.getId());

        assertThat(assignmentRepository.findById(assignmentId).orElseThrow().getAssignedByUserId()).isNull();
        AgentRegistryAuditEntry audit = registryAuditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(fixture.agent().getId())
                .stream().filter(entry -> entry.getEventType() == AgentRegistryAuditEventType.AGENT_PROJECT_ASSIGNED)
                .findFirst().orElseThrow();
        assertThat(audit.getActorUserId()).isEqualTo(admin.getId());
    }

    private void assertCanManage(String path, User actor, UUID projectId) throws Exception {
        mockMvc.perform(get(path).with(user(new No8doUserDetails(actor)))).andExpect(status().isOk());
        mockMvc.perform(put(path + "/" + projectId).with(user(new No8doUserDetails(actor))).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(path + "/" + projectId).with(user(new No8doUserDetails(actor))).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private void assertDenied(String path, User actor, UUID projectId) throws Exception {
        mockMvc.perform(get(path).with(user(new No8doUserDetails(actor)))).andExpect(status().isForbidden());
        mockMvc.perform(put(path + "/" + projectId).with(user(new No8doUserDetails(actor))).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(path + "/" + projectId).with(user(new No8doUserDetails(actor))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    private void assertAnonymousDenied(String path, UUID projectId) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(path + "/" + projectId).with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(path + "/" + projectId).with(csrf())).andExpect(status().isUnauthorized());
    }

    private void assertSafe(MvcResult result) throws Exception {
        assertSafeNode(objectMapper.readTree(result.getResponse().getContentAsString()));
        assertThat(result.getResponse().getContentAsString()).doesNotContain("project-private-description", "repo-private-value");
    }

    private static void assertSafeNode(JsonNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                assertThat(FORBIDDEN_FIELDS).doesNotContain(field.getKey().toLowerCase());
                assertSafeNode(field.getValue());
            });
        } else if (node.isArray()) {
            node.forEach(AgentProjectAssignmentControllerIntegrationTests::assertSafeNode);
        } else if (node.isTextual()) {
            assertThat(node.asText().toLowerCase()).doesNotContain("secret", "fingerprint", "authorization", "token");
        }
    }

    private Fixture fixture(String prefix) {
        User owner = newUser(prefix + "-owner");
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace(prefix + " " + UUID.randomUUID()));
        workspaceIds.add(workspace.getId());
        addMember(workspace.getId(), owner, WorkspaceRole.OWNER);
        Agent agent = agentRegistryService.createAgent(workspace.getId(), owner.getId(), "Agent " + prefix, null, null);
        agentIds.add(agent.getId());
        return new Fixture(owner, workspace.getId(), agent);
    }

    private Project project(UUID workspaceId, User actor, String name) {
        Project project = new Project(workspaceRepository.findById(workspaceId).orElseThrow(), name, actor);
        project.setDescription("project-private-description");
        project.setRepositoryUrl("https://example.test/repo-private-value");
        Project saved = projectRepository.saveAndFlush(project);
        projectIds.add(saved.getId());
        return saved;
    }

    private User newUser(String prefix) {
        String suffix = UUID.randomUUID().toString();
        User user = userRepository.saveAndFlush(new User(prefix, prefix + "-" + suffix + "@example.test", "hash"));
        userIds.add(user.getId());
        return user;
    }

    private void addMember(UUID workspaceId, User user, WorkspaceRole role) {
        memberRepository.saveAndFlush(new WorkspaceMember(workspaceRepository.findById(workspaceId).orElseThrow(), user, role));
    }

    private static String path(UUID workspaceId, UUID agentId) {
        return ROOT.formatted(workspaceId, agentId);
    }

    private record Fixture(User owner, UUID workspaceId, Agent agent) {}
}
