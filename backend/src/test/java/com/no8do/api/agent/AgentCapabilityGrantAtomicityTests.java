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
class AgentCapabilityGrantAtomicityTests {
    @MockitoBean private AgentRegistryAuditEntryRepository auditRepository;

    @Autowired private AgentRegistryService agentRegistryService;
    @Autowired private AgentCapabilityGrantService grantService;
    @Autowired private AgentCapabilityGrantRepository grantRepository;
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
    void grantAuditFailureRollsBackTheGrant() {
        fixture = fixture("capability-grant-atomic");
        grantService.revoke(fixture.workspaceId(), fixture.agentId(), AgentCapability.REPLAY_CREATE, fixture.actorId());
        assertThat(hasGrant(AgentCapability.REPLAY_CREATE)).isFalse();
        doThrow(new IllegalStateException("audit unavailable")).when(auditRepository).saveAndFlush(any());

        assertThatThrownBy(() -> grantService.grant(fixture.workspaceId(), fixture.agentId(),
                AgentCapability.REPLAY_CREATE, fixture.actorId()))
                .isInstanceOf(IllegalStateException.class).hasMessage("audit unavailable");

        assertThat(hasGrant(AgentCapability.REPLAY_CREATE)).isFalse();
    }

    @Test
    void revokeAuditFailureRollsBackTheDeletion() {
        fixture = fixture("capability-revoke-atomic");
        assertThat(hasGrant(AgentCapability.REPLAY_READ)).isTrue();
        doThrow(new IllegalStateException("audit unavailable")).when(auditRepository).saveAndFlush(any());

        assertThatThrownBy(() -> grantService.revoke(fixture.workspaceId(), fixture.agentId(),
                AgentCapability.REPLAY_READ, fixture.actorId()))
                .isInstanceOf(IllegalStateException.class).hasMessage("audit unavailable");

        assertThat(hasGrant(AgentCapability.REPLAY_READ)).isTrue();
    }

    private boolean hasGrant(AgentCapability capability) {
        return grantRepository.findByAgent_IdOrderByGrantedAtAscCapabilityAsc(fixture.agentId()).stream()
                .anyMatch(grant -> grant.getCapability() == capability);
    }

    private Fixture fixture(String prefix) {
        Fixture created = new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = UUID.randomUUID().toString();
            User owner = userRepository.save(new User(prefix, prefix + "-" + suffix + "@example.test", "hash"));
            Workspace workspace = workspaceRepository.save(new Workspace(prefix + " " + suffix));
            memberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
            return new Fixture(owner.getId(), workspace.getId(), null);
        });
        Fixture initial = java.util.Objects.requireNonNull(created);
        Agent agent = agentRegistryService.createAgent(initial.workspaceId(), initial.actorId(), "Agent " + prefix,
                null, null);
        fixture = new Fixture(initial.actorId(), initial.workspaceId(), agent.getId());
        return fixture;
    }

    private record Fixture(UUID actorId, UUID workspaceId, UUID agentId) {}
}
