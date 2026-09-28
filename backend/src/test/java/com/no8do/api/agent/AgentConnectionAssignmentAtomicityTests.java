package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.no8do.api.connection.ConnectionCredentialReferenceType;
import com.no8do.api.connection.ConnectionRepository;
import com.no8do.api.connection.ConnectionService;
import com.no8do.api.connection.CreateConnectionRequest;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class AgentConnectionAssignmentAtomicityTests {

    @MockitoBean private AgentRegistryAuditEntryRepository auditRepository;
    @Autowired private AgentRegistryService agentRegistryService;
    @Autowired private AgentConnectionAssignmentService assignmentService;
    @Autowired private AgentConnectionAssignmentRepository assignmentRepository;
    @Autowired private AgentRepository agentRepository;
    @Autowired private ConnectionService connectionService;
    @Autowired private ConnectionRepository connectionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TestData data;

    @AfterEach
    void cleanup() {
        if (data == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            memberRepository.deleteByWorkspaceId(data.workspaceId());
            workspaceRepository.deleteById(data.workspaceId());
            userRepository.deleteById(data.actorId());
        });
    }

    @Test
    void assignmentAuditFailureRollsBackTheInsertedAssignment() {
        data = fixture("connection-assignment-atomic-create");
        doThrow(new IllegalStateException("audit unavailable")).when(auditRepository).saveAndFlush(any());

        assertThatThrownBy(() -> assignmentService.assign(data.workspaceId(), data.agentId(),
                data.connectionId(), data.actorId()))
                .isInstanceOf(IllegalStateException.class).hasMessage("audit unavailable");

        assertThat(assignmentRepository.countByAgent_Id(data.agentId())).isZero();
    }

    @Test
    void unassignmentAuditFailureRollsBackTheDeletedAssignment() {
        data = fixture("connection-assignment-atomic-delete");
        assignmentService.assign(data.workspaceId(), data.agentId(), data.connectionId(), data.actorId());
        assertThat(assignmentRepository.countByAgent_Id(data.agentId())).isEqualTo(1);
        doThrow(new IllegalStateException("audit unavailable")).when(auditRepository).saveAndFlush(any());

        assertThatThrownBy(() -> assignmentService.unassign(data.workspaceId(), data.agentId(),
                data.connectionId(), data.actorId()))
                .isInstanceOf(IllegalStateException.class).hasMessage("audit unavailable");

        assertThat(assignmentRepository.countByAgent_Id(data.agentId())).isEqualTo(1);
    }

    private TestData fixture(String prefix) {
        InitialData created = new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = UUID.randomUUID().toString();
            User owner = userRepository.save(new User(prefix, prefix + "-" + suffix + "@example.test", "hash"));
            Workspace workspace = workspaceRepository.save(new Workspace(prefix + " " + suffix));
            memberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
            return new InitialData(owner.getId(), workspace.getId());
        });
        InitialData initial = java.util.Objects.requireNonNull(created);
        Agent agent = agentRegistryService.createAgent(initial.workspaceId(), initial.actorId(),
                "Agent " + prefix, null, null);
        var connection = connectionService.create(initial.workspaceId(), initial.actorId(),
                new CreateConnectionRequest("GITHUB", "Connection " + prefix,
                        ConnectionCredentialReferenceType.NONE, null, null));
        data = new TestData(initial.actorId(), initial.workspaceId(), agent.getId(), connection.id());
        return data;
    }

    private record TestData(UUID actorId, UUID workspaceId, UUID agentId, UUID connectionId) {}
    private record InitialData(UUID actorId, UUID workspaceId) {}
}
