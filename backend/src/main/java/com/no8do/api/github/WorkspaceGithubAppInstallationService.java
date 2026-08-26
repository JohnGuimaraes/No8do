package com.no8do.api.github;

import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceGithubAppInstallationService {

    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final WorkspaceGithubAppInstallationRepository installationRepository;

    public WorkspaceGithubAppInstallationService(
            WorkspaceAuthorizationService workspaceAuthorizationService,
            WorkspaceRepository workspaceRepository,
            UserRepository userRepository,
            WorkspaceGithubAppInstallationRepository installationRepository
    ) {
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.installationRepository = installationRepository;
    }

    @Transactional(readOnly = true)
    public WorkspaceGithubAppInstallationResponse get(UUID workspaceId, UUID currentUserId) {
        requireManager(workspaceId, currentUserId);
        return installationRepository.findById(workspaceId)
            .map(WorkspaceGithubAppInstallationResponse::from)
            .orElseGet(WorkspaceGithubAppInstallationResponse::notInstalled);
    }

    @Transactional(readOnly = true)
    public WorkspaceGithubAppStatusResponse getStatus(UUID workspaceId, UUID currentUserId) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        return installationRepository.findById(workspaceId)
            .map(WorkspaceGithubAppStatusResponse::from)
            .orElseGet(WorkspaceGithubAppStatusResponse::notConfigured);
    }

    @Transactional
    public WorkspaceGithubAppInstallationResponse registerVerifiedInstallation(
            UUID workspaceId,
            UUID currentUserId,
            GithubAppInstallationMetadata metadata
    ) {
        requireManager(workspaceId, currentUserId);
        validateMetadata(metadata);

        WorkspaceGithubAppInstallation installation = installationRepository.findById(workspaceId).orElse(null);
        if (installation == null) {
            installation = new WorkspaceGithubAppInstallation(
                workspaceRepository.getReferenceById(workspaceId),
                userRepository.getReferenceById(currentUserId),
                metadata
            );
            installation = installationRepository.save(installation);
        } else if (sameMetadata(installation, metadata)) {
            return WorkspaceGithubAppInstallationResponse.from(installation);
        } else {
            installation.replaceMetadata(metadata);
        }
        return WorkspaceGithubAppInstallationResponse.from(installation);
    }

    private boolean sameMetadata(WorkspaceGithubAppInstallation installation, GithubAppInstallationMetadata metadata) {
        return installation.getInstallationId() == metadata.installationId()
            && installation.getAccountId() == metadata.accountId()
            && installation.getAccountLogin().equals(metadata.accountLogin())
            && installation.getAccountType() == metadata.accountType();
    }

    private void validateMetadata(GithubAppInstallationMetadata metadata) {
        if (metadata == null || metadata.installationId() <= 0 || metadata.accountId() <= 0
                || metadata.accountLogin() == null || metadata.accountLogin().isBlank() || metadata.accountLogin().length() > 255
                || metadata.accountType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub App installation metadata is invalid");
        }
    }

    private void requireManager(UUID workspaceId, UUID currentUserId) {
        workspaceAuthorizationService.requireWorkspaceRole(workspaceId, currentUserId, WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
    }
}
