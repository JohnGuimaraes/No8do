package com.no8do.api.github;

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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkspaceGithubAppStatusControllerTests {

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

    @Test
    void letsWorkspaceMembersReadOnlyTheSafeGithubAppStatus() throws Exception {
        User owner = createUser();
        User admin = createUser();
        User member = createUser();
        User outsider = createUser();
        Workspace workspace = workspaceRepository.save(new Workspace("Workspace " + UUID.randomUUID()));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, admin, WorkspaceRole.ADMIN));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, member, WorkspaceRole.MEMBER));
        installationRepository.save(new WorkspaceGithubAppInstallation(workspace, owner,
            new GithubAppInstallationMetadata(42L, 7L, "no8do-org", GithubAppInstallationAccountType.ORGANIZATION)));

        assertSafeStatus(workspace, owner);
        assertSafeStatus(workspace, admin);
        assertSafeStatus(workspace, member);

        mockMvc.perform(get("/api/workspaces/{workspaceId}/integrations/github/app", workspace.getId())
                .with(user(new No8doUserDetails(outsider))))
            .andExpect(status().isForbidden());
    }

    private void assertSafeStatus(Workspace workspace, User user) throws Exception {
        mockMvc.perform(get("/api/workspaces/{workspaceId}/integrations/github/app", workspace.getId())
                .with(user(new No8doUserDetails(user))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.configured").value(true))
            .andExpect(jsonPath("$.accountLogin").value("no8do-org"))
            .andExpect(jsonPath("$.accountType").value("ORGANIZATION"))
            .andExpect(jsonPath("$.installationId").doesNotExist())
            .andExpect(jsonPath("$.accountId").doesNotExist())
            .andExpect(jsonPath("$.configuredAt").doesNotExist())
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andExpect(jsonPath("$.token").doesNotExist())
            .andExpect(jsonPath("$.privateKey").doesNotExist())
            .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }

    private User createUser() {
        UUID id = UUID.randomUUID();
        return userRepository.save(new User("User " + id, "github-app-status-" + id + "@example.com", "hash"));
    }
}
