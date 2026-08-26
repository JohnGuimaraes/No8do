package com.no8do.api.github;

import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceGithubAppInstallationFlowService {

    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final WorkspaceGithubAppInstallStateRepository stateRepository;
    private final WorkspaceGithubAppInstallationService installationService;
    private final GithubAppClient githubAppClient;
    private final String frontendUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public WorkspaceGithubAppInstallationFlowService(
            WorkspaceAuthorizationService workspaceAuthorizationService,
            WorkspaceRepository workspaceRepository,
            UserRepository userRepository,
            WorkspaceGithubAppInstallStateRepository stateRepository,
            WorkspaceGithubAppInstallationService installationService,
            GithubAppClient githubAppClient,
            @Value("${no8do.frontend-url}") String frontendUrl
    ) {
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.stateRepository = stateRepository;
        this.installationService = installationService;
        this.githubAppClient = githubAppClient;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public String start(UUID workspaceId, UUID currentUserId) {
        requireManager(workspaceId, currentUserId);
        String state = generateState();
        stateRepository.save(new WorkspaceGithubAppInstallState(
            state,
            workspaceRepository.getReferenceById(workspaceId),
            userRepository.getReferenceById(currentUserId),
            Instant.now().plus(STATE_TTL)
        ));
        return githubAppClient.installationUrl(state);
    }

    public String finish(String code, String state, Long installationId, UUID currentUserId) {
        if (code == null || code.isBlank() || state == null || state.isBlank() || installationId == null || installationId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub App installation response is invalid");
        }
        WorkspaceGithubAppInstallState installState = stateRepository
            .findByStateAndExpiresAtAfterAndConsumedAtIsNull(state, Instant.now())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub App installation state is invalid"));
        if (!installState.getUser().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "GitHub App installation state does not belong to the current user");
        }
        if (stateRepository.markConsumed(installState.getId(), Instant.now()) != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub App installation state is invalid");
        }

        UUID workspaceId = installState.getWorkspace().getId();
        try {
            GithubAppInstallationMetadata metadata = githubAppClient.exchangeCodeAndFindInstallation(code, installationId);
            installationService.registerVerifiedInstallation(workspaceId, currentUserId, metadata);
        } catch (ResponseStatusException exception) {
            throw new WorkspaceGithubAppInstallationCallbackException(workspaceId, exception);
        }
        return settingsUrl(workspaceId, "installed");
    }

    public String errorUrl() {
        return frontendUrl + "/?githubApp=error";
    }

    public String settingsErrorUrl(UUID workspaceId) {
        return settingsUrl(workspaceId, "error");
    }

    private String settingsUrl(UUID workspaceId, String result) {
        return frontendUrl + "/w/" + workspaceId + "/settings?githubApp=" + result;
    }

    private String generateState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void requireManager(UUID workspaceId, UUID currentUserId) {
        workspaceAuthorizationService.requireWorkspaceRole(workspaceId, currentUserId, WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
    }
}
