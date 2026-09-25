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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class AgentSessionRevocationServiceTests {
    @Autowired private AgentSessionRevocationService revocationService;
    @Autowired private AgentSessionRegistry registry;
    @Autowired private AgentSessionRepository sessionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceRepository workspaceRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

    @Test
    void workspaceOwnerAndAdminCanRevokeButSessionOwnerAloneDoesNotGrantAuthority() {
        assertWorkspaceRoleCanRevoke(WorkspaceRole.OWNER);
        assertWorkspaceRoleCanRevoke(WorkspaceRole.ADMIN);
        assertWorkspaceRoleIsDenied(WorkspaceRole.MEMBER);
        assertWorkspaceRoleIsDenied(WorkspaceRole.VIEWER);
    }

    @Test
    void nullWorkspaceCanOnlyBeRevokedByItsOwner() {
        User owner = createUser("null-workspace-owner");
        User other = createUser("null-workspace-other");
        AgentSession session = createSession(owner, null);

        assertThatThrownBy(() -> revocationService.revoke(session.getId(), other.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(failure -> ((ResponseStatusException) failure).getStatusCode().value())
                .isEqualTo(404);

        AgentSessionRevocationResult result = revocationService.revoke(session.getId(), owner.getId());
        assertThat(result.newlyRevoked()).isTrue();
        assertThat(result.revokedByUserId()).isEqualTo(owner.getId());
    }

    @Test
    void repeatedRevocationPreservesOriginalTimestampAndActor() {
        WorkspaceSetup setup = workspaceSetup("idempotent", WorkspaceRole.ADMIN);
        User secondManager = createUser("idempotent-owner");
        workspaceMemberRepository.save(new WorkspaceMember(setup.workspace(), secondManager, WorkspaceRole.OWNER));
        AgentSession session = createSession(setup.sessionOwner(), setup.workspace());

        AgentSessionRevocationResult first = revocationService.revoke(session.getId(), setup.actor().getId());
        AgentSessionRevocationResult repeated = revocationService.revoke(session.getId(), secondManager.getId());

        assertThat(first.newlyRevoked()).isTrue();
        assertThat(repeated.newlyRevoked()).isFalse();
        assertThat(repeated.revokedAt()).isEqualTo(first.revokedAt());
        assertThat(repeated.revokedByUserId()).isEqualTo(setup.actor().getId());
        assertThat(sessionRepository.findById(session.getId()).orElseThrow().getRevokedAt()).isEqualTo(first.revokedAt());
    }

    @Test
    void unknownSessionAndUnauthorizedWorkspaceAreIndistinguishableNotFound() {
        WorkspaceSetup setup = workspaceSetup("not-found", WorkspaceRole.MEMBER);
        AgentSession session = createSession(setup.sessionOwner(), setup.workspace());

        assertNotFound(() -> revocationService.revoke(session.getId(), setup.actor().getId()));
        assertNotFound(() -> revocationService.revoke(UUID.randomUUID(), setup.actor().getId()));
    }

    private void assertWorkspaceRoleCanRevoke(WorkspaceRole role) {
        WorkspaceSetup setup = workspaceSetup("allowed-" + role.name().toLowerCase(), role);
        AgentSession session = createSession(setup.sessionOwner(), setup.workspace());
        AgentSessionRevocationResult result = revocationService.revoke(session.getId(), setup.actor().getId());
        assertThat(result.newlyRevoked()).isTrue();
        assertThat(result.revokedByUserId()).isEqualTo(setup.actor().getId());
    }

    private void assertWorkspaceRoleIsDenied(WorkspaceRole role) {
        WorkspaceSetup setup = workspaceSetup("denied-" + role.name().toLowerCase(), role);
        AgentSession session = createSession(setup.sessionOwner(), setup.workspace());
        assertNotFound(() -> revocationService.revoke(session.getId(), setup.actor().getId()));
        assertNotFound(() -> revocationService.revoke(session.getId(), setup.sessionOwner().getId()));
    }

    private void assertNotFound(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOf(ResponseStatusException.class)
                .extracting(failure -> ((ResponseStatusException) failure).getStatusCode().value())
                .isEqualTo(404);
    }

    private WorkspaceSetup workspaceSetup(String suffix, WorkspaceRole actorRole) {
        User actor = createUser("revoker-" + suffix);
        User sessionOwner = createUser("session-owner-" + suffix);
        Workspace workspace = workspaceRepository.save(new Workspace("Revocation " + suffix));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, actor, actorRole));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, sessionOwner, WorkspaceRole.MEMBER));
        return new WorkspaceSetup(actor, sessionOwner, workspace);
    }

    private AgentSession createSession(User owner, Workspace workspace) {
        AgentSessionRegistrationRequest request = new AgentSessionRegistrationRequest("test-agent", "1.0",
                workspace == null ? null : workspace.getId(), AgentTransport.MCP,
                UUID.randomUUID().toString().replace("-", "").repeat(2));
        AgentSessionResponse response = registry.register(owner.getId(), request);
        return sessionRepository.findById(response.sessionId()).orElseThrow();
    }

    private User createUser(String prefix) {
        String id = UUID.randomUUID().toString();
        return userRepository.save(new User(prefix + "-" + id, id + "@example.test", "hash"));
    }

    private record WorkspaceSetup(User actor, User sessionOwner, Workspace workspace) {}
}
