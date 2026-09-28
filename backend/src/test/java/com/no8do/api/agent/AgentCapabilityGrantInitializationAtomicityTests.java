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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class AgentCapabilityGrantInitializationAtomicityTests {
    @MockitoBean private AgentCapabilityGrantRepository grantRepository;

    @Autowired private AgentRegistryService agentRegistryService;
    @Autowired private AgentRepository agentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private Fixture fixture;

    @AfterEach
    void cleanup() {
        if (fixture == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            memberRepository.deleteByWorkspaceId(fixture.workspaceId());
            workspaceRepository.deleteById(fixture.workspaceId());
            userRepository.deleteById(fixture.actorId());
        });
    }

    @Test
    void failureToPersistInitialPublishedGrantsRollsBackAgentCreation() {
        fixture = new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = UUID.randomUUID().toString();
            User owner = userRepository.save(new User("grant-init", "grant-init-" + suffix + "@example.test", "hash"));
            Workspace workspace = workspaceRepository.save(new Workspace("grant-init " + suffix));
            memberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
            return new Fixture(owner.getId(), workspace.getId());
        });
        doThrow(new IllegalStateException("grant storage unavailable")).when(grantRepository).saveAllAndFlush(any());

        assertThatThrownBy(() -> agentRegistryService.createAgent(fixture.workspaceId(), fixture.actorId(),
                "Must roll back", null, null))
                .isInstanceOf(IllegalStateException.class).hasMessage("grant storage unavailable");

        assertThat(agentRepository.findByWorkspaceIdOrderByUpdatedAtDescIdAsc(fixture.workspaceId())).isEmpty();
    }

    private record Fixture(UUID actorId, UUID workspaceId) {}
}
