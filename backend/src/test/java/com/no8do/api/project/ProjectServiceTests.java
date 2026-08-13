package com.no8do.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.no8do.api.activity.ProjectActivity;
import com.no8do.api.activity.ProjectActivityRepository;
import com.no8do.api.activity.ProjectActivityType;
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
class ProjectServiceTests {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectActivityRepository projectActivityRepository;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Test
    void memberListsProjectsFromWorkspace() {
        TestData data = createMember();
        Project project = projectRepository.save(new Project(data.workspace(), "Projeto", data.user()));
        TestData otherData = createMember();
        projectRepository.save(new Project(otherData.workspace(), "Outro projeto", otherData.user()));

        assertThat(projectService.listByWorkspace(data.workspace().getId(), data.user().getId()))
            .extracting(ProjectResponse::id)
            .containsExactly(project.getId());
    }

    @Test
    void nonMemberDoesNotListProjects() {
        TestData data = createMember();
        User outsider = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));

        assertThatThrownBy(() -> projectService.listByWorkspace(data.workspace().getId(), outsider.getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );
    }

    @Test
    void userOutsideWorkspaceDoesNotCreateProjectOrAutomaticActivity() {
        TestData data = createMember();
        User outsider = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        long activitiesBefore = projectActivityRepository.count();

        assertThatThrownBy(() -> projectService.create(
            data.workspace().getId(),
            outsider.getId(),
            new CreateProjectRequest("Projeto", null, null, null)
        ))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );

        assertThat(projectActivityRepository.count()).isEqualTo(activitiesBefore);
    }

    @Test
    void memberCreatesProject() {
        TestData data = createMember();

        ProjectResponse response = projectService.create(
            data.workspace().getId(),
            data.user().getId(),
            new CreateProjectRequest("  Projeto novo  ", "Descricao", null, "Estado atual")
        );

        assertThat(response.name()).isEqualTo("Projeto novo");
        assertThat(response.description()).isEqualTo("Descricao");
        assertThat(response.status()).isEqualTo(ProjectStatus.IDEA);
        assertThat(response.currentState()).isEqualTo("Estado atual");
        assertThat(projectRepository.findById(response.id())).isPresent();
    }

    @Test
    void createdProjectReceivesCurrentUserAsCreatedBy() {
        TestData data = createMember();

        ProjectResponse response = projectService.create(
            data.workspace().getId(),
            data.user().getId(),
            new CreateProjectRequest("Projeto", null, ProjectStatus.ACTIVE, null)
        );

        assertThat(response.createdBy()).isEqualTo(data.user().getId());
    }

    @Test
    void createdProjectBelongsToWorkspace() {
        TestData data = createMember();

        ProjectResponse response = projectService.create(
            data.workspace().getId(),
            data.user().getId(),
            new CreateProjectRequest("Projeto", null, null, null)
        );

        assertThat(response.workspaceId()).isEqualTo(data.workspace().getId());
    }

    @Test
    void createProjectCreatesAutomaticActivity() {
        TestData data = createMember();

        ProjectResponse response = projectService.create(
            data.workspace().getId(),
            data.user().getId(),
            new CreateProjectRequest("Projeto", null, null, null)
        );

        assertThat(projectActivityRepository.findByProjectIdOrderByCreatedAtDesc(response.id()))
            .hasSize(1)
            .first()
            .satisfies(activity -> {
                assertThat(activity.getProject().getId()).isEqualTo(response.id());
                assertThat(activity.getCreatedBy().getId()).isEqualTo(data.user().getId());
                assertThat(activity.getType()).isEqualTo(ProjectActivityType.UPDATE);
                assertThat(activity.getContent()).isEqualTo("Projeto criado.");
            });
    }

    @Test
    void projectOutsideWorkspaceReturnsNotFound() {
        TestData data = createMember();
        TestData otherData = createMember();
        Project project = projectRepository.save(new Project(data.workspace(), "Projeto", data.user()));

        assertThatThrownBy(() -> projectService.getByWorkspace(
            otherData.workspace().getId(),
            project.getId(),
            otherData.user().getId()
        ))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
    }

    @Test
    void updateChangesAllowedFields() {
        TestData data = createMember();
        Project project = projectRepository.save(new Project(data.workspace(), "Projeto", data.user()));

        ProjectResponse response = projectService.update(
            data.workspace().getId(),
            project.getId(),
            data.user().getId(),
            new UpdateProjectRequest("  Projeto atualizado  ", "Nova descricao", ProjectStatus.BLOCKED, "Novo estado")
        );

        assertThat(response.name()).isEqualTo("Projeto atualizado");
        assertThat(response.description()).isEqualTo("Nova descricao");
        assertThat(response.status()).isEqualTo(ProjectStatus.BLOCKED);
        assertThat(response.currentState()).isEqualTo("Novo estado");
    }

    @Test
    void updateNameCreatesAutomaticActivity() {
        TestData data = createMember();
        Project project = projectRepository.save(new Project(data.workspace(), "Projeto", data.user()));

        projectService.update(
            data.workspace().getId(),
            project.getId(),
            data.user().getId(),
            new UpdateProjectRequest("Projeto atualizado", null, null, null)
        );

        assertThat(projectActivityRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()))
            .extracting(ProjectActivity::getContent)
            .containsExactly("Projeto atualizado: nome alterado.");
    }

    @Test
    void updateStatusCreatesAutomaticActivityWithOldAndNewStatus() {
        TestData data = createMember();
        Project project = projectRepository.save(new Project(data.workspace(), "Projeto", data.user()));

        projectService.update(
            data.workspace().getId(),
            project.getId(),
            data.user().getId(),
            new UpdateProjectRequest("Projeto", null, ProjectStatus.ACTIVE, null)
        );

        assertThat(projectActivityRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()))
            .extracting(ProjectActivity::getContent)
            .containsExactly("Projeto atualizado: status alterado de IDEA para ACTIVE.");
    }

    @Test
    void updateDescriptionAndCurrentStateCreatesAutomaticActivity() {
        TestData data = createMember();
        Project project = projectRepository.save(new Project(data.workspace(), "Projeto", data.user()));

        projectService.update(
            data.workspace().getId(),
            project.getId(),
            data.user().getId(),
            new UpdateProjectRequest("Projeto", "Nova descricao", null, "Novo estado")
        );

        assertThat(projectActivityRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()))
            .extracting(ProjectActivity::getContent)
            .containsExactly("Projeto atualizado: descrição alterada; estado atual alterado.");
    }

    @Test
    void updateWithoutRealChangesDoesNotCreateAutomaticActivity() {
        TestData data = createMember();
        Project project = projectRepository.save(new Project(data.workspace(), "Projeto", data.user()));

        projectService.update(
            data.workspace().getId(),
            project.getId(),
            data.user().getId(),
            new UpdateProjectRequest("Projeto", null, ProjectStatus.IDEA, null)
        );

        assertThat(projectActivityRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).isEmpty();
    }

    @Test
    void blankNameUpdateDoesNotCreateAutomaticActivity() {
        TestData data = createMember();
        Project project = projectRepository.save(new Project(data.workspace(), "Projeto", data.user()));

        assertThatThrownBy(() -> projectService.update(
            data.workspace().getId(),
            project.getId(),
            data.user().getId(),
            new UpdateProjectRequest("  ", "Nova descricao", ProjectStatus.ACTIVE, "Novo estado")
        ))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );

        assertThat(projectActivityRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).isEmpty();
    }

    @Test
    void userOutsideWorkspaceDoesNotUpdateProject() {
        TestData data = createMember();
        User outsider = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        Project project = projectRepository.save(new Project(data.workspace(), "Projeto", data.user()));

        assertThatThrownBy(() -> projectService.update(
            data.workspace().getId(),
            project.getId(),
            outsider.getId(),
            new UpdateProjectRequest("Projeto atualizado", null, ProjectStatus.ACTIVE, null)
        ))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );
        assertThat(projectActivityRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).isEmpty();
    }

    @Test
    void blankNameReturnsBadRequest() {
        TestData data = createMember();

        assertThatThrownBy(() -> projectService.create(
            data.workspace().getId(),
            data.user().getId(),
            new CreateProjectRequest("  ", null, null, null)
        ))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
    }

    private TestData createMember() {
        User user = userRepository.save(new User(uniqueName(), uniqueEmail(), "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Workspace " + UUID.randomUUID()));
        WorkspaceMember member = workspaceMemberRepository.save(
            new WorkspaceMember(workspace, user, WorkspaceRole.MEMBER)
        );
        return new TestData(user, workspace, member);
    }

    private String uniqueEmail() {
        return "project-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueName() {
        return "User " + UUID.randomUUID();
    }

    private record TestData(User user, Workspace workspace, WorkspaceMember member) {
    }
}
