package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class AgentRegistryAuditAtomicityTests {

    @MockitoBean private AgentRegistryAuditEntryRepository auditRepository;
    @Autowired private AgentRegistryService service;
    @Autowired private AgentRepository agentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void auditInsertFailureRollsBackAgentCreation() {
        doThrow(new IllegalStateException("audit unavailable")).when(auditRepository).saveAndFlush(any());
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        TestData data = transactions.execute(status -> {
            User owner = userRepository.save(new User("Atomic owner", "atomic-" + UUID.randomUUID() + "@example.com", "hash"));
            Workspace workspace = workspaceRepository.save(new Workspace("Atomic " + UUID.randomUUID()));
            workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
            return new TestData(owner.getId(), workspace.getId());
        });
        assertThat(data).isNotNull();
        long before = agentRepository.count();

        assertThatThrownBy(() -> service.createAgent(data.workspaceId(), data.actorUserId(), "Atomic", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");

        assertThat(agentRepository.count()).isEqualTo(before);
        transactions.executeWithoutResult(status -> {
            workspaceMemberRepository.deleteByWorkspaceId(data.workspaceId());
            workspaceRepository.deleteById(data.workspaceId());
            userRepository.deleteById(data.actorUserId());
        });
    }

    private record TestData(UUID actorUserId, UUID workspaceId) {}
}
