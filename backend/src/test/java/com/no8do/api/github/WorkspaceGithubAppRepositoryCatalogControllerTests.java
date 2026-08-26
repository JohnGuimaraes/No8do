package com.no8do.api.github;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.no8do.api.auth.No8doUserDetails;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkspaceGithubAppRepositoryCatalogControllerTests {

    @Autowired
    private MockMvc mockMvc;

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
    void allowsMembersToReadRepositoriesAuthorizedForTheWorkspaceInstallation() throws Exception {
        User owner = createUser();
        User member = createUser();
        Workspace workspace = createWorkspace(owner);
        workspaceMemberRepository.save(new WorkspaceMember(workspace, member, WorkspaceRole.MEMBER));
        installationRepository.save(new WorkspaceGithubAppInstallation(workspace, owner,
            new GithubAppInstallationMetadata(42L, 7L, "octo", GithubAppInstallationAccountType.USER)));
        GithubAppInstallationAccessToken token = new GithubAppInstallationAccessToken("temporary", Instant.parse("2030-01-01T00:00:00Z"));
        when(githubAppClient.createInstallationAccessToken(42L)).thenReturn(token);
        when(githubAppClient.listInstallationRepositories(token, 2, 10)).thenReturn(new GithubAppRepositoryPageResponse(List.of(repository()), 2, 10, 1));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/integrations/github/app/repositories", workspace.getId())
                .param("page", "2").param("perPage", "10")
                .with(user(new No8doUserDetails(member))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.perPage").value(10))
            .andExpect(jsonPath("$.items[0].repositoryId").value(1L))
            .andExpect(jsonPath("$.items[0].private").value(true))
            .andExpect(jsonPath("$.items[0].fullName").value("octo/private-repository"));
    }

    @Test
    void rejectsUsersOutsideTheWorkspaceBeforeCallingGithub() throws Exception {
        User owner = createUser();
        User outsider = createUser();
        Workspace workspace = createWorkspace(owner);

        mockMvc.perform(get("/api/workspaces/{workspaceId}/integrations/github/app/repositories", workspace.getId())
                .with(user(new No8doUserDetails(outsider))))
            .andExpect(status().isForbidden());

        verifyNoInteractions(githubAppClient);
    }

    @Test
    void allowsMembersToPreviewOnlyRepositoriesAuthorizedForTheInstallation() throws Exception {
        User owner = createUser();
        User member = createUser();
        Workspace workspace = createWorkspace(owner);
        workspaceMemberRepository.save(new WorkspaceMember(workspace, member, WorkspaceRole.MEMBER));
        installationRepository.save(new WorkspaceGithubAppInstallation(workspace, owner,
            new GithubAppInstallationMetadata(42L, 7L, "octo", GithubAppInstallationAccountType.USER)));
        GithubAppInstallationAccessToken token = new GithubAppInstallationAccessToken("temporary", Instant.parse("2030-01-01T00:00:00Z"));
        when(githubAppClient.createInstallationAccessToken(42L)).thenReturn(token);
        when(githubAppClient.previewInstallationRepository(token, 1L)).thenReturn(new GithubAppRepositoryPreviewResponse(
            repository(), "# README", List.of("package.json"), List.of(GithubAppRepositoryStack.NODE_JS)
        ));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/integrations/github/app/repositories/{repositoryId}/preview", workspace.getId(), 1L)
                .with(user(new No8doUserDetails(member))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.repository.repositoryId").value(1L))
            .andExpect(jsonPath("$.readme").value("# README"))
            .andExpect(jsonPath("$.detectedStacks[0]").value("NODE_JS"));
    }

    private Workspace createWorkspace(User owner) {
        Workspace workspace = workspaceRepository.save(new Workspace("Workspace " + UUID.randomUUID()));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
        return workspace;
    }

    private User createUser() {
        UUID id = UUID.randomUUID();
        return userRepository.save(new User("User " + id, "github-catalog-" + id + "@example.com", "hash"));
    }

    private GithubAppRepositoryResponse repository() {
        return new GithubAppRepositoryResponse(1L, "private-repository", "octo/private-repository", "octo", true,
            false, false, "https://github.com/octo/private-repository", null, "main", "Java",
            Instant.parse("2026-08-24T12:00:00Z"), List.of("workspace"));
    }
}
