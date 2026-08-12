package com.no8do.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class WorkspaceAuthorizationServiceTests {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceAuthorizationService authorizationService;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Test
    void workspaceMemberCanAccessWorkspace() {
        TestData data = createMember(WorkspaceRole.MEMBER);

        WorkspaceMember member = authorizationService.requireWorkspaceMember(
            data.workspace().getId(),
            data.user().getId()
        );

        assertThat(member.getRole()).isEqualTo(WorkspaceRole.MEMBER);
        assertThat(workspaceMemberRepository.existsByWorkspaceIdAndUserId(
            data.workspace().getId(),
            data.user().getId()
        )).isTrue();
    }

    @Test
    void nonMemberIsBlockedFromWorkspace() {
        Workspace workspace = workspaceRepository.save(new Workspace("Autorizacao"));
        User user = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));

        assertThatThrownBy(() -> authorizationService.requireWorkspaceMember(workspace.getId(), user.getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );
    }

    @Test
    void ownerPassesRoleRule() {
        TestData data = createMember(WorkspaceRole.OWNER);

        WorkspaceMember member = authorizationService.requireWorkspaceRole(
            data.workspace().getId(),
            data.user().getId(),
            WorkspaceRole.OWNER,
            WorkspaceRole.ADMIN
        );

        assertThat(member.getRole()).isEqualTo(WorkspaceRole.OWNER);
    }

    @Test
    void memberDoesNotPassOwnerOrAdminRule() {
        TestData data = createMember(WorkspaceRole.MEMBER);

        assertThatThrownBy(() -> authorizationService.requireWorkspaceRole(
            data.workspace().getId(),
            data.user().getId(),
            WorkspaceRole.OWNER,
            WorkspaceRole.ADMIN
        ))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );
    }

    @Test
    void projectIsFoundOnlyInsideExpectedWorkspace() {
        TestData allowed = createMember(WorkspaceRole.MEMBER);
        TestData otherMember = createMember(WorkspaceRole.MEMBER);
        Project project = projectRepository.save(new Project(allowed.workspace(), "Projeto", allowed.user()));

        assertThat(projectRepository.existsByIdAndWorkspaceId(project.getId(), allowed.workspace().getId())).isTrue();
        assertThat(projectRepository.findByIdAndWorkspaceId(project.getId(), allowed.workspace().getId())).isPresent();
        assertThat(projectRepository.findByWorkspaceId(allowed.workspace().getId())).hasSize(1);

        assertThat(projectRepository.existsByIdAndWorkspaceId(project.getId(), otherMember.workspace().getId())).isFalse();
        assertThat(projectRepository.findByIdAndWorkspaceId(project.getId(), otherMember.workspace().getId())).isEmpty();

        authorizationService.requireProjectAccess(project.getId(), allowed.workspace().getId(), allowed.user().getId());

        assertThatThrownBy(() -> authorizationService.requireProjectAccess(
            project.getId(),
            otherMember.workspace().getId(),
            otherMember.user().getId()
        ))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
    }

    private TestData createMember(WorkspaceRole role) {
        User user = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Workspace " + UUID.randomUUID()));
        WorkspaceMember member = workspaceMemberRepository.save(new WorkspaceMember(workspace, user, role));
        return new TestData(user, workspace, member);
    }

    private String uniqueEmail() {
        return "authz-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueName() {
        return "User " + UUID.randomUUID();
    }

    private record TestData(User user, Workspace workspace, WorkspaceMember member) {
    }
}
