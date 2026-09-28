package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
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
class AgentProjectAssignmentAtomicityTests {

    @MockitoBean private AgentRegistryAuditEntryRepository auditRepository;
    @Autowired private AgentRegistryService agentRegistryService;
    @Autowired private AgentProjectAssignmentService assignmentService;
    @Autowired private AgentProjectAssignmentRepository assignmentRepository;
    @Autowired private AgentRepository agentRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TestData data;

    @AfterEach
    void cleanup() {
        if (data == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            projectRepository.deleteById(data.projectId());
            agentRepository.deleteById(data.agentId());
            memberRepository.deleteByWorkspaceId(data.workspaceId());
            workspaceRepository.deleteById(data.workspaceId());
            userRepository.deleteById(data.actorId());
        });
    }

    @Test
    void assignmentAuditFailureRollsBackTheAssignment() {
        data = fixture("assignment-atomic-create");
        doThrow(new IllegalStateException("audit unavailable")).when(auditRepository).saveAndFlush(any());

        assertThatThrownBy(() -> assignmentService.assign(data.workspaceId(), data.agentId(),
                data.projectId(), data.actorId()))
                .isInstanceOf(IllegalStateException.class).hasMessage("audit unavailable");

        assertThat(assignmentRepository.countByAgent_Id(data.agentId())).isZero();
    }

    @Test
    void unassignmentAuditFailureRollsBackTheRemoval() {
        data = fixture("assignment-atomic-delete");
        assignmentService.assign(data.workspaceId(), data.agentId(), data.projectId(), data.actorId());
        assertThat(assignmentRepository.countByAgent_Id(data.agentId())).isEqualTo(1);
        doThrow(new IllegalStateException("audit unavailable")).when(auditRepository).saveAndFlush(any());

        assertThatThrownBy(() -> assignmentService.unassign(data.workspaceId(), data.agentId(),
                data.projectId(), data.actorId()))
                .isInstanceOf(IllegalStateException.class).hasMessage("audit unavailable");

        assertThat(assignmentRepository.countByAgent_Id(data.agentId())).isEqualTo(1);
        assertThat(assignmentRepository.findByAgent_IdAndProject_Id(data.agentId(), data.projectId())).isPresent();
    }

    private TestData fixture(String prefix) {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        InitialData created = transactions.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            User owner = userRepository.save(new User(prefix, prefix + "-" + suffix + "@example.test", "hash"));
            Workspace workspace = workspaceRepository.save(new Workspace(prefix + " " + suffix));
            memberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
            return new InitialData(owner.getId(), workspace.getId());
        });
        InitialData resolved = java.util.Objects.requireNonNull(created);
        Agent agent = agentRegistryService.createAgent(resolved.workspaceId(), resolved.actorId(),
                "Agent " + prefix, null, null);
        Project project = projectRepository.saveAndFlush(new Project(
                workspaceRepository.findById(resolved.workspaceId()).orElseThrow(), "Project " + prefix,
                userRepository.findById(resolved.actorId()).orElseThrow()));
        data = new TestData(resolved.actorId(), resolved.workspaceId(), agent.getId(), project.getId());
        return data;
    }

    private record TestData(UUID actorId, UUID workspaceId, UUID agentId, UUID projectId) {}
    private record InitialData(UUID actorId, UUID workspaceId) {}
}
