package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class AgentCredentialAtomicityTests {

    @MockitoBean private AgentRegistryAuditEntryRepository auditRepository;
    @Autowired private AgentRegistryService registryService;
    @Autowired private AgentCredentialService credentialService;
    @Autowired private AgentRepository agentRepository;
    @Autowired private AgentCredentialRepository credentialRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TestData data;

    @AfterEach
    void cleanupFixture() {
        if (data == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            workspaceMemberRepository.deleteByWorkspaceId(data.workspaceId());
            workspaceRepository.deleteById(data.workspaceId());
            userRepository.deleteById(data.actorUserId());
        });
        data = null;
    }

    @Test
    void auditFailureRollsBackCredentialCreation() {
        data = fixture("credential-atomic-create");
        Agent agent = agent("Atomic create");
        doThrow(new IllegalStateException("audit unavailable")).when(auditRepository).saveAndFlush(any());

        assertThatThrownBy(() -> credentialService.create(data.workspaceId(), agent.getId(), data.actorUserId()))
                .isInstanceOf(IllegalStateException.class).hasMessage("audit unavailable");

        assertThat(credentialRepository.countByAgentIdAndStatus(agent.getId(), AgentCredentialStatus.ACTIVE)).isZero();
        assertThat(credentialRepository.countByAgentIdAndStatus(agent.getId(), AgentCredentialStatus.REVOKED)).isZero();
    }

    @Test
    void auditFailureRollsBackCredentialRevocation() {
        data = fixture("credential-atomic-revoke");
        Agent agent = agent("Atomic revoke");
        AgentCredentialIssueResponse existing = credentialService.create(data.workspaceId(), agent.getId(),
                data.actorUserId());
        doThrow(new IllegalStateException("audit unavailable")).when(auditRepository).saveAndFlush(any());

        assertThatThrownBy(() -> credentialService.revoke(data.workspaceId(), agent.getId(), existing.id(),
                data.actorUserId())).isInstanceOf(IllegalStateException.class).hasMessage("audit unavailable");

        AgentCredential unchanged = credentialRepository.findById(existing.id()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(AgentCredentialStatus.ACTIVE);
        assertThat(unchanged.getRevokedAt()).isNull();
    }

    @Test
    void auditFailureRollsBackCredentialRotation() {
        data = fixture("credential-atomic-rotate");
        Agent agent = agent("Atomic rotate");
        AgentCredentialIssueResponse existing = credentialService.create(data.workspaceId(), agent.getId(),
                data.actorUserId());
        doThrow(new IllegalStateException("audit unavailable")).when(auditRepository).saveAndFlush(any());

        assertThatThrownBy(() -> credentialService.rotate(data.workspaceId(), agent.getId(), existing.id(),
                data.actorUserId())).isInstanceOf(IllegalStateException.class).hasMessage("audit unavailable");

        assertThat(credentialRepository.countByAgentIdAndStatus(agent.getId(), AgentCredentialStatus.ACTIVE)).isEqualTo(1);
        assertThat(credentialRepository.findById(existing.id()).orElseThrow().getStatus())
                .isEqualTo(AgentCredentialStatus.ACTIVE);
    }

    @Test
    void archiveAuditFailureAfterSeveralCredentialRevocationsRollsBackEverything() {
        data = fixture("credential-atomic-archive");
        Agent archived = agent("Atomic archive target");
        Agent other = agent("Atomic archive control");
        AgentCredentialIssueResponse targetA = credentialService.create(data.workspaceId(), archived.getId(),
                data.actorUserId());
        AgentCredentialIssueResponse targetB = credentialService.create(data.workspaceId(), archived.getId(),
                data.actorUserId());
        AgentCredentialIssueResponse control = credentialService.create(data.workspaceId(), other.getId(),
                data.actorUserId());
        AtomicInteger revokeAudits = new AtomicInteger();
        doAnswer(invocation -> {
            AgentRegistryAuditEntry entry = invocation.getArgument(0);
            if (entry.getEventType() == AgentRegistryAuditEventType.AGENT_CREDENTIAL_REVOKED
                    && revokeAudits.incrementAndGet() == 2) {
                throw new IllegalStateException("audit unavailable");
            }
            return entry;
        }).when(auditRepository).saveAndFlush(any());

        assertThatThrownBy(() -> registryService.changeLifecycle(data.workspaceId(), archived.getId(),
                data.actorUserId(), AgentLifecycleStatus.ARCHIVED))
                .isInstanceOf(IllegalStateException.class).hasMessage("audit unavailable");

        assertThat(agentRepository.findById(archived.getId()).orElseThrow().getLifecycleStatus())
                .isEqualTo(AgentLifecycleStatus.ACTIVE);
        for (AgentCredentialIssueResponse credential : java.util.List.of(targetA, targetB, control)) {
            AgentCredential unchanged = credentialRepository.findById(credential.id()).orElseThrow();
            assertThat(unchanged.getStatus()).isEqualTo(AgentCredentialStatus.ACTIVE);
            assertThat(unchanged.getRevokedAt()).isNull();
        }
        assertThat(revokeAudits.get()).isEqualTo(2);
    }

    private TestData fixture(String prefix) {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        TestData created = transactions.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            User owner = userRepository.save(new User(prefix, prefix + "-" + suffix + "@example.test", "hash"));
            Workspace workspace = workspaceRepository.save(new Workspace(prefix + " " + suffix));
            workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
            return new TestData(owner.getId(), workspace.getId());
        });
        return java.util.Objects.requireNonNull(created);
    }

    private Agent agent(String name) {
        return registryService.createAgent(data.workspaceId(), data.actorUserId(), name, null, null);
    }

    private record TestData(UUID actorUserId, UUID workspaceId) {}
}
