package com.no8do.api.github;

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
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class WorkspaceGithubLinkServiceTests {

    @Autowired
    private WorkspaceGithubLinkService service;

    @Autowired
    private WorkspaceGithubLinkRepository linkRepository;

    @Autowired
    private UserGithubConnectionRepository connectionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Test
    void ownerCanLinkTheirPersonalGithubConnection() {
        TestData data = createMember(WorkspaceRole.OWNER);
        connectGithub(data.user());

        WorkspaceGithubLinkResponse response = service.linkMyGithub(data.workspace().getId(), data.user().getId());

        assertThat(response.linked()).isTrue();
        assertThat(response.login()).isEqualTo("no8do-user");
        assertThat(linkRepository.findById(data.workspace().getId()).orElseThrow().getGithubConnection().getUserId())
            .isEqualTo(data.user().getId());
    }

    @Test
    void linkingAgainReturnsTheExistingWorkspaceLink() {
        TestData data = createMember(WorkspaceRole.ADMIN);
        connectGithub(data.user());

        WorkspaceGithubLinkResponse first = service.linkMyGithub(data.workspace().getId(), data.user().getId());
        WorkspaceGithubLinkResponse repeated = service.linkMyGithub(data.workspace().getId(), data.user().getId());

        assertThat(repeated).isEqualTo(first);
        assertThat(linkRepository.findAll().stream().filter(link -> link.getWorkspaceId().equals(data.workspace().getId()))).hasSize(1);
    }

    @Test
    void linkRequiresAPersonalGithubConnection() {
        TestData data = createMember(WorkspaceRole.OWNER);

        assertThatThrownBy(() -> service.linkMyGithub(data.workspace().getId(), data.user().getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );
    }

    @Test
    void memberCannotLinkOrUnlinkGithub() {
        TestData data = createMember(WorkspaceRole.MEMBER);
        connectGithub(data.user());

        assertThatThrownBy(() -> service.linkMyGithub(data.workspace().getId(), data.user().getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );
        assertThatThrownBy(() -> service.unlink(data.workspace().getId(), data.user().getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );
    }

    @Test
    void unlinkRemovesOnlyTheWorkspaceReference() {
        TestData data = createMember(WorkspaceRole.OWNER);
        connectGithub(data.user());
        service.linkMyGithub(data.workspace().getId(), data.user().getId());

        service.unlink(data.workspace().getId(), data.user().getId());

        assertThat(linkRepository.findById(data.workspace().getId())).isEmpty();
        assertThat(connectionRepository.findById(data.user().getId())).isPresent();
    }

    private TestData createMember(WorkspaceRole role) {
        User user = userRepository.save(new User("GitHub " + UUID.randomUUID(), "github-link-" + UUID.randomUUID() + "@example.com", "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Workspace " + UUID.randomUUID()));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, user, role));
        return new TestData(user, workspace);
    }

    private void connectGithub(User user) {
        connectionRepository.save(new UserGithubConnection(user,
            new GithubOAuthUser(101L, "no8do-user", "https://avatars.githubusercontent.com/u/101", "token"),
            "ciphertext", "iv", 1));
    }

    private record TestData(User user, Workspace workspace) {
    }
}
