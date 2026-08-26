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
class WorkspaceGithubAppInstallationServiceTests {

    @Autowired
    private WorkspaceGithubAppInstallationService service;

    @Autowired
    private WorkspaceGithubAppInstallationRepository installationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Test
    void ownerCanRegisterAPersonalGithubAppInstallationWithoutPersonalTokenConnection() {
        TestData data = createMember(WorkspaceRole.OWNER);

        WorkspaceGithubAppInstallationResponse response = service.registerVerifiedInstallation(
            data.workspace().getId(), data.user().getId(), metadata(101L, 201L, "no8do-user", GithubAppInstallationAccountType.USER)
        );

        assertThat(response.installed()).isTrue();
        assertThat(response.installationId()).isEqualTo(101L);
        assertThat(response.accountLogin()).isEqualTo("no8do-user");
        assertThat(installationRepository.findById(data.workspace().getId()).orElseThrow().getAccountType())
            .isEqualTo(GithubAppInstallationAccountType.USER);
    }

    @Test
    void adminCanRegisterAnOrganizationInstallation() {
        TestData data = createMember(WorkspaceRole.ADMIN);

        WorkspaceGithubAppInstallationResponse response = service.registerVerifiedInstallation(
            data.workspace().getId(), data.user().getId(), metadata(102L, 202L, "no8do-org", GithubAppInstallationAccountType.ORGANIZATION)
        );

        assertThat(response.accountType()).isEqualTo(GithubAppInstallationAccountType.ORGANIZATION);
        assertThat(response.accountId()).isEqualTo(202L);
    }

    @Test
    void registeringTheSameInstallationAgainIsIdempotent() {
        TestData data = createMember(WorkspaceRole.OWNER);
        GithubAppInstallationMetadata metadata = metadata(103L, 203L, "no8do-user", GithubAppInstallationAccountType.USER);

        WorkspaceGithubAppInstallationResponse first = service.registerVerifiedInstallation(data.workspace().getId(), data.user().getId(), metadata);
        WorkspaceGithubAppInstallationResponse repeated = service.registerVerifiedInstallation(data.workspace().getId(), data.user().getId(), metadata);

        assertThat(repeated).isEqualTo(first);
        assertThat(installationRepository.findAll().stream().filter(installation -> installation.getWorkspaceId().equals(data.workspace().getId())))
            .hasSize(1);
    }

    @Test
    void memberCannotReadAdministrativeInstallationDetailsOrConfigureIt() {
        TestData data = createMember(WorkspaceRole.MEMBER);

        assertThatThrownBy(() -> service.get(data.workspace().getId(), data.user().getId()))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );
        assertThatThrownBy(() -> service.registerVerifiedInstallation(
            data.workspace().getId(), data.user().getId(), metadata(104L, 204L, "no8do-user", GithubAppInstallationAccountType.USER)
        ))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );
    }

    @Test
    void invalidInstallationMetadataIsRejected() {
        TestData data = createMember(WorkspaceRole.OWNER);

        assertThatThrownBy(() -> service.registerVerifiedInstallation(
            data.workspace().getId(), data.user().getId(), metadata(0L, 204L, "", GithubAppInstallationAccountType.USER)
        ))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
    }

    private TestData createMember(WorkspaceRole role) {
        User user = userRepository.save(new User("GitHub App " + UUID.randomUUID(), "github-app-" + UUID.randomUUID() + "@example.com", "hash"));
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
