package com.no8do.api.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.no8do.api.activity.ProjectActivityRepository;
import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectGithubRepositoryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectGithubRepositoryRepository projectGithubRepositoryRepository;

    @Autowired
    private ProjectActivityRepository projectActivityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private WorkspaceGithubAppInstallationRepository installationRepository;

    @MockBean
    private GithubAppClient githubAppClient;

    @Test
    void returnsNotAssociatedWhenProjectHasNoGithubRepository() throws Exception {
        TestData data = createMember();
        Project project = createProject(data);

        mockMvc.perform(get(path(data.workspace(), project)).with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("NOT_ASSOCIATED"))
            .andExpect(jsonPath("$.repositoryId").doesNotExist());

        verifyNoInteractions(githubAppClient);
    }

    @Test
    void associatesRepositoryOnlyAfterGithubAppValidatesItForProjectWorkspace() throws Exception {
        TestData data = createMember();
        Project project = createProject(data);
        GithubAppInstallationAccessToken token = configureInstallation(data.workspace(), data.user());
        when(githubAppClient.getInstallationRepository(token, 17L)).thenReturn(repository(17L));

        mockMvc.perform(put(path(data.workspace(), project)).with(user(new No8doUserDetails(data.user()))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"repositoryId\":17}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("ASSOCIATED"))
            .andExpect(jsonPath("$.repository.fullName").value("octo/repository"))
            .andExpect(jsonPath("$.token").doesNotExist());

        assertThat(projectGithubRepositoryRepository.findById(project.getId())).hasValueSatisfying(
            association -> assertThat(association.getRepositoryId()).isEqualTo(17L)
        );
        assertThat(projectActivityRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()))
            .extracting(activity -> activity.getContent())
            .contains("Repositório GitHub associado: octo/repository.");
    }

    @Test
    void rejectsRepositoryThatIsNotAccessibleToTheWorkspaceInstallation() throws Exception {
        TestData data = createMember();
        Project project = createProject(data);
        GithubAppInstallationAccessToken token = configureInstallation(data.workspace(), data.user());
        when(githubAppClient.getInstallationRepository(token, 17L))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found"));

        mockMvc.perform(put(path(data.workspace(), project)).with(user(new No8doUserDetails(data.user()))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"repositoryId\":17}"))
            .andExpect(status().isNotFound());

        assertThat(projectGithubRepositoryRepository.findById(project.getId())).isEmpty();
    }

    @Test
    void doesNotAuthorizeProjectRepositoryAccessAcrossWorkspaces() throws Exception {
        TestData data = createMember();
        TestData other = createMember();
        Project project = createProject(data);

        mockMvc.perform(put(path(other.workspace(), project)).with(user(new No8doUserDetails(other.user()))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"repositoryId\":17}"))
            .andExpect(status().isNotFound());

        verifyNoInteractions(githubAppClient);
    }

    @Test
    void replacesRepositoryAndDoesNotCreateARepositoryUrlActivity() throws Exception {
        TestData data = createMember();
        Project project = createProject(data);
        GithubAppInstallationAccessToken token = configureInstallation(data.workspace(), data.user());
        projectGithubRepositoryRepository.save(new ProjectGithubRepository(project, 17L));
        when(githubAppClient.getInstallationRepository(token, 18L)).thenReturn(repository(18L));

        mockMvc.perform(put(path(data.workspace(), project)).with(user(new No8doUserDetails(data.user()))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"repositoryId\":18}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.repositoryId").value(18L));

        assertThat(projectGithubRepositoryRepository.findById(project.getId())).hasValueSatisfying(
            association -> assertThat(association.getRepositoryId()).isEqualTo(18L)
        );
        assertThat(project.getRepositoryUrl()).isNull();
        assertThat(projectActivityRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()))
            .extracting(activity -> activity.getContent())
            .contains("Repositório GitHub alterado: octo/repository.")
            .noneMatch(content -> content.contains("repositório alterado"));
    }

    @Test
    void dissociationKeepsManualRepositoryUrlAndRegistersOneActivity() throws Exception {
        TestData data = createMember();
        Project project = createProject(data);
        project.setRepositoryUrl("https://github.com/manual/project");
        projectGithubRepositoryRepository.save(new ProjectGithubRepository(project, 17L));

        mockMvc.perform(delete(path(data.workspace(), project)).with(user(new No8doUserDetails(data.user()))).with(csrf()))
            .andExpect(status().isNoContent());

        assertThat(projectGithubRepositoryRepository.findById(project.getId())).isEmpty();
        assertThat(project.getRepositoryUrl()).isEqualTo("https://github.com/manual/project");
        assertThat(projectActivityRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()))
            .extracting(activity -> activity.getContent())
            .contains("Repositório GitHub desassociado.");
    }

    @Test
    void returnsControlledInaccessibleStateWithoutRemovingPersistedAssociation() throws Exception {
        TestData data = createMember();
        Project project = createProject(data);
        GithubAppInstallationAccessToken token = configureInstallation(data.workspace(), data.user());
        projectGithubRepositoryRepository.save(new ProjectGithubRepository(project, 17L));
        when(githubAppClient.getInstallationRepository(token, 17L))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found"));

        mockMvc.perform(get(path(data.workspace(), project)).with(user(new No8doUserDetails(data.user()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("INACCESSIBLE"))
            .andExpect(jsonPath("$.repositoryId").value(17L))
            .andExpect(jsonPath("$.repository").doesNotExist());

        assertThat(projectGithubRepositoryRepository.findById(project.getId())).isPresent();
    }

    private GithubAppInstallationAccessToken configureInstallation(Workspace workspace, User user) {
        installationRepository.save(new WorkspaceGithubAppInstallation(workspace, user,
            new GithubAppInstallationMetadata(42L, 7L, "octo", GithubAppInstallationAccountType.USER)));
        GithubAppInstallationAccessToken token = new GithubAppInstallationAccessToken("temporary", Instant.parse("2030-01-01T00:00:00Z"));
        when(githubAppClient.createInstallationAccessToken(42L)).thenReturn(token);
        return token;
    }

    private GithubAppRepositoryResponse repository(long repositoryId) {
        return new GithubAppRepositoryResponse(repositoryId, "repository", "octo/repository", "octo", true,
            false, false, "https://github.com/octo/repository", "Description", "main", "Java",
            Instant.parse("2026-08-25T12:00:00Z"), List.of("workspace"));
    }

    private String path(Workspace workspace, Project project) {
        return "/api/workspaces/" + workspace.getId() + "/projects/" + project.getId() + "/github-repository";
    }

    private Project createProject(TestData data) {
        return projectRepository.save(new Project(data.workspace(), "Project", data.user()));
    }

    private TestData createMember() {
        UUID id = UUID.randomUUID();
        User user = userRepository.save(new User("User " + id, "project-github-" + id + "@example.com", "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Workspace " + id));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, user, WorkspaceRole.MEMBER));
        return new TestData(user, workspace);
    }

    private record TestData(User user, Workspace workspace) {
    }
}
