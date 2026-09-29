package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workitem.ProjectWorkItem;
import com.no8do.api.workitem.ProjectWorkItemRepository;
import com.no8do.api.workitem.ProjectWorkItemType;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class AgentWorkItemAssignmentAtomicityTests {

    @MockitoBean private AgentRegistryAuditEntryRepository auditRepository;
    @Autowired private AgentRegistryService agentRegistryService;
    @Autowired private AgentWorkItemAssignmentService assignmentService;
    @Autowired private AgentWorkItemAssignmentRepository assignmentRepository;
    @Autowired private AgentRepository agentRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectWorkItemRepository workItemRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TestData data;

    @AfterEach
    void cleanup() {
        if (data == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            if (workItemRepository.existsById(data.workItemId())) workItemRepository.deleteById(data.workItemId());
            if (projectRepository.existsById(data.projectId())) projectRepository.deleteById(data.projectId());
            if (agentRepository.existsById(data.agentId())) agentRepository.deleteById(data.agentId());
            memberRepository.deleteByWorkspaceId(data.workspaceId());
            if (workspaceRepository.existsById(data.workspaceId())) workspaceRepository.deleteById(data.workspaceId());
            if (userRepository.existsById(data.actorId())) userRepository.deleteById(data.actorId());
        });
    }

    @Test
    void assignmentAuditFailureRollsBackTheInsertedAssignment() {
        data = fixture("work-item-assignment-atomic-create");
        doThrow(new IllegalStateException("audit unavailable")).when(auditRepository).saveAndFlush(any());

        assertThatThrownBy(() -> assignmentService.assign(data.workspaceId(), data.agentId(),
                data.workItemId(), data.actorId()))
                .isInstanceOf(IllegalStateException.class).hasMessage("audit unavailable");

        assertThat(assignmentRepository.countByAgent_Id(data.agentId())).isZero();
        assertThat(assignmentRepository.findByAgent_IdAndWorkItem_Id(data.agentId(), data.workItemId())).isEmpty();
    }

    @Test
    void unassignmentAuditFailureRollsBackTheRemoval() {
        data = fixture("work-item-assignment-atomic-delete");
        assignmentService.assign(data.workspaceId(), data.agentId(), data.workItemId(), data.actorId());
        assertThat(assignmentRepository.countByAgent_Id(data.agentId())).isEqualTo(1);
        doThrow(new IllegalStateException("audit unavailable")).when(auditRepository).saveAndFlush(any());

        assertThatThrownBy(() -> assignmentService.unassign(data.workspaceId(), data.agentId(),
                data.workItemId(), data.actorId()))
                .isInstanceOf(IllegalStateException.class).hasMessage("audit unavailable");

        assertThat(assignmentRepository.countByAgent_Id(data.agentId())).isEqualTo(1);
        assertThat(assignmentRepository.findByAgent_IdAndWorkItem_Id(data.agentId(), data.workItemId())).isPresent();
    }

    private TestData fixture(String prefix) {
        InitialData initial = new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = UUID.randomUUID().toString();
            User owner = userRepository.save(new User(prefix, prefix + "-" + suffix + "@example.test", "hash"));
            Workspace workspace = workspaceRepository.save(new Workspace(prefix + " " + suffix));
            memberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
            return new InitialData(owner.getId(), workspace.getId());
        });
        InitialData created = java.util.Objects.requireNonNull(initial);
        Agent agent = agentRegistryService.createAgent(created.workspaceId(), created.actorId(),
                "Agent " + prefix, null, null);
        Project project = projectRepository.saveAndFlush(new Project(
                workspaceRepository.findById(created.workspaceId()).orElseThrow(), "Project " + prefix,
                userRepository.findById(created.actorId()).orElseThrow()));
        ProjectWorkItem workItem = workItemRepository.saveAndFlush(new ProjectWorkItem(project,
                ProjectWorkItemType.NEXT_STEP, "Work item " + prefix, "private details", null));
        data = new TestData(created.actorId(), created.workspaceId(), agent.getId(), project.getId(), workItem.getId());
        return data;
    }

    private record TestData(UUID actorId, UUID workspaceId, UUID agentId, UUID projectId, UUID workItemId) {}
    private record InitialData(UUID actorId, UUID workspaceId) {}
}
