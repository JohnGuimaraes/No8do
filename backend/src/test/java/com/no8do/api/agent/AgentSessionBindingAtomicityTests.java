package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class AgentSessionBindingAtomicityTests {
    @MockitoBean private AgentAuditEntryRepository auditEntryRepository;
    @Autowired private AgentSessionRegistry sessionRegistry;
    @Autowired private AgentRegistryService agentRegistryService;
    @Autowired private AgentCredentialService credentialService;
    @Autowired private AgentSessionRepository sessionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private UUID actorId;
    private UUID workspaceId;
    private String fingerprint;

    @AfterEach
    void cleanup() {
        if (workspaceId == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            workspaceMemberRepository.deleteByWorkspaceId(workspaceId);
            workspaceRepository.deleteById(workspaceId);
            userRepository.deleteById(actorId);
        });
        workspaceId = null;
    }

    @Test
    void mandatoryBindingAuditFailureRollsBackSessionAndBinding() {
        String suffix = UUID.randomUUID().toString();
        User actor = userRepository.saveAndFlush(new User("binding-audit", suffix + "@example.test", "hash"));
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("binding audit " + suffix));
        workspaceMemberRepository.saveAndFlush(new WorkspaceMember(workspace, actor, WorkspaceRole.OWNER));
        actorId = actor.getId();
        workspaceId = workspace.getId();
        Agent agent = agentRegistryService.createAgent(workspaceId, actorId, "Atomic binding", null, null);
        AgentCredentialIssueResponse credential = credentialService.create(workspaceId, agent.getId(), actorId);
        fingerprint = UUID.randomUUID().toString().replace("-", "").repeat(2);
        AgentSessionRegistrationRequest request = new AgentSessionRegistrationRequest("Codex", "1", workspaceId,
                AgentTransport.MCP, fingerprint);

        assertThatThrownBy(() -> sessionRegistry.register(actorId, request, credential.credential()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Audit obrigatório de vínculo de AgentSession não foi inserido.");

        assertThat(sessionRepository.findByTransportAndTransportSessionFingerprint(
                AgentTransport.MCP, fingerprint)).isEmpty();
    }
}
