package com.no8do.api.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class WorkspaceGithubAppInstallationFlowServiceTests {

    @Autowired
    private WorkspaceGithubAppInstallationFlowService flowService;

    @Autowired
    private WorkspaceGithubAppInstallationRepository installationRepository;

    @Autowired
    private WorkspaceGithubAppInstallStateRepository stateRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @MockBean
    private GithubAppClient githubAppClient;

    @Test
    void ownerCanInstallForAPersonalAccount() {
        TestData data = createMember(WorkspaceRole.OWNER);
        String state = start(data);
        when(githubAppClient.exchangeCodeAndFindInstallation(eq("owner-code"), eq(101L)))
            .thenReturn(metadata(101L, 201L, "no8do-user", GithubAppInstallationAccountType.USER));

        String redirect = flowService.finish("owner-code", state, 101L, data.user().getId());

        assertThat(redirect).contains("/w/" + data.workspace().getId() + "/settings?githubApp=installed");
        assertThat(installationRepository.findById(data.workspace().getId()).orElseThrow().getAccountType())
            .isEqualTo(GithubAppInstallationAccountType.USER);
    }

    @Test
    void adminCanInstallForAnOrganization() {
        TestData data = createMember(WorkspaceRole.ADMIN);
        String state = start(data);
        when(githubAppClient.exchangeCodeAndFindInstallation(eq("admin-code"), eq(102L)))
            .thenReturn(metadata(102L, 202L, "no8do-org", GithubAppInstallationAccountType.ORGANIZATION));

        flowService.finish("admin-code", state, 102L, data.user().getId());

        assertThat(installationRepository.findById(data.workspace().getId()).orElseThrow().getAccountLogin()).isEqualTo("no8do-org");
    }

    @Test
    void memberCannotStartAnInstallation() {
        TestData data = createMember(WorkspaceRole.MEMBER);

        assertThatThrownBy(() -> flowService.start(data.workspace().getId(), data.user().getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );
    }

    @Test
    void invalidOrExpiredStateIsRejected() {
        TestData data = createMember(WorkspaceRole.OWNER);
        stateRepository.save(new WorkspaceGithubAppInstallState("expired-state", data.workspace(), data.user(), Instant.now().minusSeconds(1)));

        assertThatThrownBy(() -> flowService.finish("code", "invalid-state", 103L, data.user().getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        assertThatThrownBy(() -> flowService.finish("code", "expired-state", 103L, data.user().getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
    }

    @Test
    void callbackCannotUseAStateCreatedByAnotherUser() {
        TestData owner = createMember(WorkspaceRole.OWNER);
        TestData otherOwner = createMember(WorkspaceRole.OWNER);
        String state = start(owner);

        assertThatThrownBy(() -> flowService.finish("code", state, 103L, otherOwner.user().getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );
    }

    @Test
    void forgedOrInaccessibleInstallationDoesNotGetPersisted() {
        TestData data = createMember(WorkspaceRole.OWNER);
        String state = start(data);
        when(githubAppClient.exchangeCodeAndFindInstallation(eq("forged-code"), eq(999L)))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub App installation could not be verified"));

        assertThatThrownBy(() -> flowService.finish("forged-code", state, 999L, data.user().getId()))
            .isInstanceOf(WorkspaceGithubAppInstallationCallbackException.class);
        assertThat(installationRepository.findById(data.workspace().getId())).isEmpty();
    }

    @Test
    void callbackStateCannotBeReusedAndDoesNotDuplicateTheInstallation() {
        TestData data = createMember(WorkspaceRole.OWNER);
        String state = start(data);
        when(githubAppClient.exchangeCodeAndFindInstallation(eq("repeat-code"), eq(104L)))
            .thenReturn(metadata(104L, 204L, "no8do-user", GithubAppInstallationAccountType.USER));

        flowService.finish("repeat-code", state, 104L, data.user().getId());

        assertThatThrownBy(() -> flowService.finish("repeat-code", state, 104L, data.user().getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        assertThat(installationRepository.findAll().stream().filter(installation -> installation.getWorkspaceId().equals(data.workspace().getId())))
            .hasSize(1);
    }

    private String start(TestData data) {
        when(githubAppClient.installationUrl(anyString())).thenAnswer(invocation -> "https://github.com/apps/no8do/installations/new?state=" + invocation.getArgument(0));
        String url = flowService.start(data.workspace().getId(), data.user().getId());
        return url.substring(url.indexOf("state=") + "state=".length());
    }

    private TestData createMember(WorkspaceRole role) {
        User user = userRepository.save(new User("GitHub App Flow " + UUID.randomUUID(), "github-app-flow-" + UUID.randomUUID() + "@example.com", "hash"));
        Workspace workspace = workspaceRepository.save(new Workspace("Workspace " + UUID.randomUUID()));
        workspaceMemberRepository.save(new WorkspaceMember(workspace, user, role));
        return new TestData(user, workspace);
    }

    private GithubAppInstallationMetadata metadata(long installationId, long accountId, String accountLogin,
            GithubAppInstallationAccountType accountType) {
        return new GithubAppInstallationMetadata(installationId, accountId, accountLogin, accountType);
    }

    private record TestData(User user, Workspace workspace) {
    }
}
