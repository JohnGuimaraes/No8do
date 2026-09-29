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
import com.no8do.api.activity.ProjectActivityRepository;
import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workitem.ProjectWorkItem;
import com.no8do.api.workitem.ProjectWorkItemRepository;
import com.no8do.api.workitem.ProjectWorkItemStatus;
import com.no8do.api.workitem.ProjectWorkItemType;
import com.no8do.api.workitem.ProjectWorkItemService;
import com.no8do.api.workitem.UpdateProjectWorkItemStatusRequest;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class AgentWorkItemAssignmentControllerIntegrationTests {

    private static final String ROOT = "/api/workspaces/%s/agents/%s/work-items";
    private static final String PRIVATE_DETAILS = "work-item-private-details-must-not-leak";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private AgentRepository agentRepository;
    @Autowired private AgentRegistryService agentRegistryService;
    @Autowired private AgentWorkItemAssignmentRepository assignmentRepository;
    @Autowired private AgentWorkItemAssignmentService assignmentService;
    @Autowired private AgentRegistryAuditEntryRepository registryAuditRepository;
    @Autowired private AgentProjectAssignmentRepository projectAssignmentRepository;
    @Autowired private AgentProjectAssignmentService projectAssignmentService;
    @Autowired private AgentCapabilityGrantRepository capabilityGrantRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectActivityRepository projectActivityRepository;
    @Autowired private ProjectWorkItemRepository workItemRepository;
    @Autowired private ProjectWorkItemService workItemService;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<UUID> workspaceIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> agentIds = new ArrayList<>();
    private final List<UUID> projectIds = new ArrayList<>();
    private final List<UUID> workItemIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (UUID workItemId : workItemIds) {
                if (workItemRepository.existsById(workItemId)) workItemRepository.deleteById(workItemId);
            }
            for (UUID projectId : projectIds) {
                projectActivityRepository.deleteByProjectId(projectId);
                if (projectRepository.existsById(projectId)) projectRepository.deleteById(projectId);
            }
            for (UUID agentId : agentIds) {
                if (agentRepository.existsById(agentId)) agentRepository.deleteById(agentId);
            }
            for (UUID workspaceId : workspaceIds) {
                memberRepository.deleteByWorkspaceId(workspaceId);
                if (workspaceRepository.existsById(workspaceId)) workspaceRepository.deleteById(workspaceId);
            }
            for (UUID userId : userIds) {
                if (userRepository.existsById(userId)) userRepository.deleteById(userId);
            }
        });
        workItemIds.clear();
        projectIds.clear();
        agentIds.clear();
        workspaceIds.clear();
        userIds.clear();
    }

    @Test
    void ownerAndAdminCanListAssignAndUnassignIdempotentlyWithSafeAuditAndActivity() throws Exception {
        Fixture fixture = fixture("work-item-rbac");
        User admin = newUser("work-item-admin");
        User member = newUser("work-item-member");
        User viewer = newUser("work-item-viewer");
        User outsider = newUser("work-item-outsider");
        User humanAssignee = newUser("work-item-human-assignee");
        addMember(fixture.workspaceId(), admin, WorkspaceRole.ADMIN);
        addMember(fixture.workspaceId(), member, WorkspaceRole.MEMBER);
        addMember(fixture.workspaceId(), viewer, WorkspaceRole.VIEWER);
        addMember(fixture.workspaceId(), humanAssignee, WorkspaceRole.MEMBER);
        ProjectWorkItem first = workItem(fixture, "Owner assignment", humanAssignee);
        ProjectWorkItem second = workItem(fixture, "Admin assignment", null);
        String path = path(fixture.workspaceId(), fixture.agent().getId());

        assertReadRole(path, fixture.owner(), 200);
        assertReadRole(path, admin, 200);
        assertReadRole(path, member, 403);
        assertReadRole(path, viewer, 403);
        assertReadRole(path, outsider, 403);
        assertAnonymous(path, first.getId());
        assertWriteDenied(path, member, first.getId());
        assertWriteDenied(path, viewer, first.getId());
        assertWriteDenied(path, outsider, first.getId());

        assignTwice(path, fixture.owner(), first.getId());
        AgentWorkItemAssignment firstAssignment = assignment(fixture.agent().getId(), first.getId());
        Instant assignedAt = firstAssignment.getAssignedAt();
        assertThat(firstAssignment.getAssignedByUserId()).isEqualTo(fixture.owner().getId());
        assertThat(workItemRepository.findById(first.getId()).orElseThrow().getAssignee().getId())
                .isEqualTo(humanAssignee.getId());

        assignTwice(path, admin, second.getId());
        MvcResult list = mockMvc.perform(get(path).with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listJson = objectMapper.readTree(list.getResponse().getContentAsString());
        assertThat(listJson.size()).isEqualTo(2);
        assertThat(listJson.toString()).doesNotContain(PRIVATE_DETAILS, "assignedByUserId", "assignmentId",
                "actorUserId", "secretHash", "credential", "token", "AgentSession");
        JsonNode firstJson = findByWorkItemId(listJson, first.getId());
        assertThat(firstJson.path("projectId").asText()).isEqualTo(fixture.project().getId().toString());
        assertThat(firstJson.path("projectName").asText()).isEqualTo(fixture.project().getName());
        assertThat(firstJson.path("title").asText()).isEqualTo("Owner assignment");
        assertThat(firstJson.path("type").asText()).isEqualTo("NEXT_STEP");
        assertThat(firstJson.path("status").asText()).isEqualTo("OPEN");
        assertThat(firstJson.path("dueDate").asText()).isEqualTo("2026-10-02");
        assertThat(Instant.parse(firstJson.get("assignedAt").asText())).isEqualTo(assignedAt);

        List<AgentRegistryAuditEntry> audits = registryAuditRepository
                .findByAgentIdOrderByOccurredAtAscIdAsc(fixture.agent().getId());
        assertThat(audits).filteredOn(entry -> entry.getEventType() == AgentRegistryAuditEventType.AGENT_WORK_ITEM_ASSIGNED)
                .hasSize(2);
        AgentRegistryAuditEntry assignedAudit = audits.stream()
                .filter(entry -> entry.getEventType() == AgentRegistryAuditEventType.AGENT_WORK_ITEM_ASSIGNED)
                .filter(entry -> entry.getMetadata().path("workItemId").asText().equals(first.getId().toString()))
                .findFirst().orElseThrow();
        assertThat(assignedAudit.getMetadata().path("agentId").asText()).isEqualTo(fixture.agent().getId().toString());
        assertThat(assignedAudit.getMetadata().path("projectId").asText()).isEqualTo(fixture.project().getId().toString());
        assertThat(assignedAudit.getMetadata().toString()).doesNotContain(PRIVATE_DETAILS, "assigneeUserId");

        MvcResult activity = mockMvc.perform(get("/api/workspaces/%s/agents/%s/activity"
                        .formatted(fixture.workspaceId(), fixture.agent().getId()))
                        .with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk()).andReturn();
        JsonNode activityJson = objectMapper.readTree(activity.getResponse().getContentAsString());
        assertThat(activityJson.toString()).contains("AGENT_WORK_ITEM_ASSIGNED", first.getId().toString(),
                fixture.project().getId().toString());
        assertThat(activityJson.toString()).doesNotContain(PRIVATE_DETAILS, "actorUserId", "workspaceId",
                "assignedByUserId");

        unassignTwice(path, fixture.owner(), first.getId());
        unassignTwice(path, admin, second.getId());
        assertThat(assignmentRepository.countByAgent_Id(fixture.agent().getId())).isZero();
        assertThat(eventCount(fixture.agent().getId(), AgentRegistryAuditEventType.AGENT_WORK_ITEM_ASSIGNED)).isEqualTo(2);
        assertThat(eventCount(fixture.agent().getId(), AgentRegistryAuditEventType.AGENT_WORK_ITEM_UNASSIGNED)).isEqualTo(2);
        assertThat(workItemRepository.findById(first.getId()).orElseThrow().getAssignee().getId())
                .isEqualTo(humanAssignee.getId());
        assertThat(firstAssignment.getAssignedAt()).isEqualTo(assignedAt);
    }

    @Test
    void assignmentsAreManyToManyAndIndependentFromProjectAssignmentsAndCapabilities() {
        Fixture fixture = fixture("work-item-many");
        Agent secondAgent = agent(fixture, "Second agent");
        ProjectWorkItem first = workItem(fixture, "First item", null);
        ProjectWorkItem second = workItem(fixture, "Second item", null);
        long grantsBefore = capabilityGrantRepository.countByAgent_Id(fixture.agent().getId());

        assignmentService.assign(fixture.workspaceId(), fixture.agent().getId(), first.getId(), fixture.owner().getId());
        assignmentService.assign(fixture.workspaceId(), fixture.agent().getId(), second.getId(), fixture.owner().getId());
        assignmentService.assign(fixture.workspaceId(), secondAgent.getId(), first.getId(), fixture.owner().getId());

        assertThat(assignmentRepository.findWorkItemSummaries(fixture.workspaceId(), fixture.agent().getId()))
                .extracting(AgentWorkItemAssignmentResponse::workItemId).containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(assignmentRepository.findWorkItemSummaries(fixture.workspaceId(), secondAgent.getId()))
                .extracting(AgentWorkItemAssignmentResponse::workItemId).containsExactly(first.getId());
        assertThat(projectAssignmentRepository.countByAgent_Id(fixture.agent().getId())).isZero();
        assertThat(capabilityGrantRepository.countByAgent_Id(fixture.agent().getId())).isEqualTo(grantsBefore);

        projectAssignmentService.assign(fixture.workspaceId(), fixture.agent().getId(), fixture.project().getId(),
                fixture.owner().getId());
        projectAssignmentService.unassign(fixture.workspaceId(), fixture.agent().getId(), fixture.project().getId(),
                fixture.owner().getId());
        assertThat(assignmentRepository.countByAgent_Id(fixture.agent().getId())).isEqualTo(2);
        assertThat(projectAssignmentRepository.countByAgent_Id(fixture.agent().getId())).isZero();
        assertThat(capabilityGrantRepository.countByAgent_Id(fixture.agent().getId())).isEqualTo(grantsBefore);
    }

    @Test
    void workspaceIsolationHidesMissingAndCrossWorkspaceAgentsAndWorkItems() throws Exception {
        Fixture fixture = fixture("work-item-tenant-a");
        Fixture other = fixture("work-item-tenant-b");
        ProjectWorkItem otherItem = workItem(other, "Private cross-workspace item", null);
        String path = path(fixture.workspaceId(), fixture.agent().getId());
        String otherAgentPath = path(fixture.workspaceId(), other.agent().getId());

        MvcResult crossWorkspace = mockMvc.perform(put(path + "/" + otherItem.getId())
                        .with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNotFound()).andReturn();
        MvcResult missing = mockMvc.perform(put(path + "/" + UUID.randomUUID())
                        .with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNotFound()).andReturn();
        assertThat(crossWorkspace.getResponse().getContentAsString())
                .isEqualTo(missing.getResponse().getContentAsString());
        mockMvc.perform(get(path(fixture.workspaceId(), UUID.randomUUID()))
                        .with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(otherAgentPath).with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(path + "/" + otherItem.getId())
                        .with(user(new No8doUserDetails(fixture.owner()))).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(assignmentRepository.countByAgent_Id(fixture.agent().getId())).isZero();
        assertThat(assignmentRepository.countByAgent_Id(other.agent().getId())).isZero();
    }

    @Test
    void lifecycleAndWorkItemStatusRulesPreserveExistingAssignments() throws Exception {
        Fixture fixture = fixture("work-item-lifecycle");
        ProjectWorkItem item = workItem(fixture, "Lifecycle item", null);
        ProjectWorkItem reopenedItem = workItem(fixture, "Reopened assignment item", null);
        ProjectWorkItem doneItem = workItem(fixture, "Already done", null);
        doneItem.setStatus(ProjectWorkItemStatus.DONE);
        workItemRepository.saveAndFlush(doneItem);
        String path = path(fixture.workspaceId(), fixture.agent().getId());

        assignmentService.assign(fixture.workspaceId(), fixture.agent().getId(), item.getId(), fixture.owner().getId());
        workItemService.updateStatus(fixture.workspaceId(), fixture.project().getId(), item.getId(),
                fixture.owner().getId(), new UpdateProjectWorkItemStatusRequest(ProjectWorkItemStatus.DONE));
        MvcResult doneList = mockMvc.perform(get(path).with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workItemId").value(item.getId().toString()))
                .andExpect(jsonPath("$[0].status").value("DONE"))
                .andReturn();
        assertThat(doneList.getResponse().getContentAsString()).doesNotContain(PRIVATE_DETAILS);
        mockMvc.perform(delete(path + "/" + item.getId()).with(user(new No8doUserDetails(fixture.owner())))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(assignmentRepository.findByAgent_IdAndWorkItem_Id(fixture.agent().getId(), item.getId())).isEmpty();

        mockMvc.perform(put(path + "/" + doneItem.getId()).with(user(new No8doUserDetails(fixture.owner())))
                        .with(csrf()))
                .andExpect(status().isConflict());
        assertThat(assignmentRepository.countByAgent_Id(fixture.agent().getId())).isZero();

        assignmentService.assign(fixture.workspaceId(), fixture.agent().getId(), reopenedItem.getId(),
                fixture.owner().getId());
        workItemService.updateStatus(fixture.workspaceId(), fixture.project().getId(), reopenedItem.getId(),
                fixture.owner().getId(), new UpdateProjectWorkItemStatusRequest(ProjectWorkItemStatus.DONE));
        workItemService.updateStatus(fixture.workspaceId(), fixture.project().getId(), reopenedItem.getId(),
                fixture.owner().getId(), new UpdateProjectWorkItemStatusRequest(ProjectWorkItemStatus.OPEN));
        assertThat(assignmentRepository.findWorkItemSummaries(fixture.workspaceId(), fixture.agent().getId()))
                .extracting(AgentWorkItemAssignmentResponse::workItemId).containsExactly(reopenedItem.getId());

        agentRegistryService.changeLifecycle(fixture.workspaceId(), fixture.agent().getId(), fixture.owner().getId(),
                AgentLifecycleStatus.DISABLED);
        ProjectWorkItem disabledItem = workItem(fixture, "Disabled Agent target", null);
        mockMvc.perform(put(path + "/" + disabledItem.getId()).with(user(new No8doUserDetails(fixture.owner())))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(path + "/" + disabledItem.getId()).with(user(new No8doUserDetails(fixture.owner())))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        ProjectWorkItem archivedTarget = workItem(fixture, "Archived Agent target", null);
        agentRegistryService.changeLifecycle(fixture.workspaceId(), fixture.agent().getId(), fixture.owner().getId(),
                AgentLifecycleStatus.ARCHIVED);
        mockMvc.perform(put(path + "/" + archivedTarget.getId()).with(user(new No8doUserDetails(fixture.owner())))
                        .with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(get(path).with(user(new No8doUserDetails(fixture.owner()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].workItemId").value(reopenedItem.getId().toString()));
        mockMvc.perform(delete(path + "/" + reopenedItem.getId()).with(user(new No8doUserDetails(fixture.owner())))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(assignmentRepository.countByAgent_Id(fixture.agent().getId())).isZero();
    }

    @Test
    void deletingAgentOrWorkItemCascadesAssignmentsWithoutDeletingTheOtherAggregate() {
        Fixture fixture = fixture("work-item-cascade");
        ProjectWorkItem first = workItem(fixture, "Agent cascade", null);
        assignmentService.assign(fixture.workspaceId(), fixture.agent().getId(), first.getId(), fixture.owner().getId());
        UUID firstAgentId = fixture.agent().getId();
        agentRepository.deleteById(firstAgentId);
        assertThat(assignmentRepository.countByAgent_Id(firstAgentId)).isZero();
        assertThat(workItemRepository.existsById(first.getId())).isTrue();
        assertThat(registryAuditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(firstAgentId))
                .extracting(AgentRegistryAuditEntry::getEventType)
                .contains(AgentRegistryAuditEventType.AGENT_WORK_ITEM_ASSIGNED);

        Agent secondAgent = agent(fixture, "Work item cascade agent");
        ProjectWorkItem second = workItem(fixture, "WorkItem cascade", null);
        assignmentService.assign(fixture.workspaceId(), secondAgent.getId(), second.getId(), fixture.owner().getId());
        workItemRepository.deleteById(second.getId());
        assertThat(assignmentRepository.countByAgent_Id(secondAgent.getId())).isZero();
        assertThat(agentRepository.existsById(secondAgent.getId())).isTrue();
    }

    private void assertReadRole(String path, User actor, int expected) throws Exception {
        mockMvc.perform(get(path).with(user(new No8doUserDetails(actor)))).andExpect(status().is(expected));
    }

    private void assertWriteDenied(String path, User actor, UUID workItemId) throws Exception {
        mockMvc.perform(put(path + "/" + workItemId).with(user(new No8doUserDetails(actor))).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(path + "/" + workItemId).with(user(new No8doUserDetails(actor))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    private void assertAnonymous(String path, UUID workItemId) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(path + "/" + workItemId).with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(path + "/" + workItemId).with(csrf())).andExpect(status().isUnauthorized());
    }

    private void assignTwice(String path, User actor, UUID workItemId) throws Exception {
        mockMvc.perform(put(path + "/" + workItemId).with(user(new No8doUserDetails(actor))).with(csrf()))
                .andExpect(status().isNoContent());
        AgentWorkItemAssignment beforeNoOp = assignmentByWorkItem(workItemId);
        Instant assignedAt = beforeNoOp.getAssignedAt();
        UUID assignmentId = beforeNoOp.getId();
        mockMvc.perform(put(path + "/" + workItemId).with(user(new No8doUserDetails(actor))).with(csrf()))
                .andExpect(status().isNoContent());
        AgentWorkItemAssignment afterNoOp = assignmentByWorkItem(workItemId);
        assertThat(afterNoOp.getId()).isEqualTo(assignmentId);
        assertThat(afterNoOp.getAssignedAt()).isEqualTo(assignedAt);
    }

    private void unassignTwice(String path, User actor, UUID workItemId) throws Exception {
        mockMvc.perform(delete(path + "/" + workItemId).with(user(new No8doUserDetails(actor))).with(csrf()))
                .andExpect(status().isNoContent());
        long countAfterDelete = eventCountForWorkItem(AgentRegistryAuditEventType.AGENT_WORK_ITEM_UNASSIGNED,
                workItemId);
        mockMvc.perform(delete(path + "/" + workItemId).with(user(new No8doUserDetails(actor))).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(eventCountForWorkItem(AgentRegistryAuditEventType.AGENT_WORK_ITEM_UNASSIGNED, workItemId))
                .isEqualTo(countAfterDelete);
    }

    private long eventCount(UUID agentId, AgentRegistryAuditEventType type) {
        return registryAuditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(agentId).stream()
                .filter(entry -> entry.getEventType() == type).count();
    }

    private long eventCountForWorkItem(AgentRegistryAuditEventType type, UUID workItemId) {
        return registryAuditRepository.findByAgentIdOrderByOccurredAtAscIdAsc(
                        assignmentRepository.findByAgent_IdAndWorkItem_Id(agentIds.get(agentIds.size() - 1), workItemId)
                                .map(assignment -> assignment.getAgent().getId()).orElse(agentIds.get(0)))
                .stream().filter(entry -> entry.getEventType() == type
                        && entry.getMetadata().path("workItemId").asText().equals(workItemId.toString())).count();
    }

    private AgentWorkItemAssignment assignment(UUID agentId, UUID workItemId) {
        return assignmentRepository.findByAgent_IdAndWorkItem_Id(agentId, workItemId).orElseThrow();
    }

    private AgentWorkItemAssignment assignmentByWorkItem(UUID workItemId) {
        return assignmentRepository.findAll().stream()
                .filter(row -> row.getWorkItem().getId().equals(workItemId)).findFirst().orElseThrow();
    }

    private ProjectWorkItem createWorkItem(Fixture fixture, String title, User humanAssignee) {
        ProjectWorkItem item = new ProjectWorkItem(fixture.project(), ProjectWorkItemType.NEXT_STEP, title,
                PRIVATE_DETAILS, fixture.owner());
        item.setDueDate(LocalDate.of(2026, 10, 2));
        item.setAssignee(humanAssignee);
        ProjectWorkItem saved = workItemRepository.saveAndFlush(item);
        workItemIds.add(saved.getId());
        return saved;
    }

    private ProjectWorkItem workItem(Fixture fixture, String title, User humanAssignee) {
        return createWorkItem(fixture, title, humanAssignee);
    }

    private Fixture fixture(String prefix) {
        User owner = newUser(prefix + "-owner");
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace(prefix + " " + UUID.randomUUID()));
        workspaceIds.add(workspace.getId());
        memberRepository.saveAndFlush(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
        Agent agent = agentRegistryService.createAgent(workspace.getId(), owner.getId(), "Agent " + prefix, null, null);
        agentIds.add(agent.getId());
        Project project = projectRepository.saveAndFlush(new Project(workspace, "Project " + prefix, owner));
        projectIds.add(project.getId());
        return new Fixture(owner, workspace.getId(), agent, project);
    }

    private Agent agent(Fixture fixture, String name) {
        Agent agent = agentRegistryService.createAgent(fixture.workspaceId(), fixture.owner().getId(), name, null, null);
        agentIds.add(agent.getId());
        return agent;
    }

    private User newUser(String prefix) {
        String suffix = UUID.randomUUID().toString();
        User created = userRepository.saveAndFlush(new User(prefix, prefix + "-" + suffix + "@example.test", "hash"));
        userIds.add(created.getId());
        return created;
    }

    private void addMember(UUID workspaceId, User user, WorkspaceRole role) {
        memberRepository.saveAndFlush(new WorkspaceMember(workspaceRepository.findById(workspaceId).orElseThrow(),
                user, role));
    }

    private static JsonNode findByWorkItemId(JsonNode rows, UUID workItemId) {
        for (JsonNode row : rows) {
            if (row.path("workItemId").asText().equals(workItemId.toString())) return row;
        }
        throw new AssertionError("WorkItem assignment was not listed");
    }

    private static String path(UUID workspaceId, UUID agentId) {
        return ROOT.formatted(workspaceId, agentId);
    }

    private record Fixture(User owner, UUID workspaceId, Agent agent, Project project) {}
}
