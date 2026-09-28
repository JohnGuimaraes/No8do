package com.no8do.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AgentCapabilityGrantEffectiveResolutionTests {
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository memberRepository;
    @Autowired private AgentRegistryService agentRegistryService;
    @Autowired private AgentCredentialService credentialService;
    @Autowired private AgentSessionRegistry sessionRegistry;
    @Autowired private AgentSessionContextResolver contextResolver;
    @Autowired private AgentSessionContextService contextService;
    @Autowired private AgentCapabilityGrantService grantService;

    @Test
    void boundSessionUsesLivePersistentGrantIntersectionAndLegacySessionKeepsPriorRule() {
        String suffix = UUID.randomUUID().toString();
        User owner = userRepository.save(new User("effective-owner", "effective-" + suffix + "@example.test", "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("effective " + suffix));
        memberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
        Agent agent = agentRegistryService.createAgent(workspace.getId(), owner.getId(), "Bound", null, null);
        var issued = credentialService.create(workspace.getId(), agent.getId(), owner.getId());

        AgentSessionResponse bound = sessionRegistry.register(owner.getId(), request(workspace.getId()), issued.credential());
        AgentSessionContext context = contextResolver.resolve(bound.sessionId(), owner.getId());
        assertThat(context.effectiveCapabilities()).contains(AgentCapability.REPLAY_CREATE, AgentCapability.REPLAY_READ);

        grantService.revoke(workspace.getId(), agent.getId(), AgentCapability.REPLAY_CREATE, owner.getId());
        assertThat(contextResolver.resolve(bound.sessionId(), owner.getId()).effectiveCapabilities())
                .doesNotContain(AgentCapability.REPLAY_CREATE);
        grantService.grant(workspace.getId(), agent.getId(), AgentCapability.REPLAY_CREATE, owner.getId());
        assertThat(contextResolver.resolve(bound.sessionId(), owner.getId()).effectiveCapabilities())
                .contains(AgentCapability.REPLAY_CREATE);

        contextService.updateRuntimeMode(bound.sessionId(), owner.getId(), AgentRuntimeMode.READ_ONLY);
        assertThat(contextResolver.resolve(bound.sessionId(), owner.getId()).effectiveCapabilities())
                .doesNotContain(AgentCapability.REPLAY_CREATE)
                .contains(AgentCapability.REPLAY_READ);
        grantService.revoke(workspace.getId(), agent.getId(), AgentCapability.REPLAY_READ, owner.getId());
        assertThat(contextResolver.resolve(bound.sessionId(), owner.getId()).effectiveCapabilities())
                .doesNotContain(AgentCapability.REPLAY_READ);
        grantService.grant(workspace.getId(), agent.getId(), AgentCapability.REPLAY_READ, owner.getId());
        assertThat(contextResolver.resolve(bound.sessionId(), owner.getId()).effectiveCapabilities())
                .contains(AgentCapability.REPLAY_READ);

        AgentSessionResponse legacy = sessionRegistry.register(owner.getId(), request(workspace.getId()));
        AgentSessionContext legacyContext = contextResolver.resolve(legacy.sessionId(), owner.getId());
        assertThat(legacyContext.session().getAgent()).isNull();
        assertThat(legacyContext.effectiveCapabilities()).contains(AgentCapability.REPLAY_CREATE,
                AgentCapability.REPLAY_READ);
    }

    private static AgentSessionRegistrationRequest request(UUID workspaceId) {
        return new AgentSessionRegistrationRequest("Capability test", "1", workspaceId, AgentTransport.MCP,
                UUID.randomUUID().toString().replace("-", "").substring(0, 32).repeat(2));
    }
}
