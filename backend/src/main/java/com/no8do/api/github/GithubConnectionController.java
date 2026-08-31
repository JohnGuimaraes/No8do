package com.no8do.api.github;

import com.no8do.api.auth.No8doUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class GithubConnectionController {

    private final UserGithubConnectionService userConnectionService;
    private final WorkspaceGithubLinkService workspaceLinkService;
    private final WorkspaceGithubAppInstallationService appInstallationService;
    private final WorkspaceGithubAppInstallationFlowService appInstallationFlowService;
    private final WorkspaceGithubAppRepositoryCatalogService repositoryCatalogService;

    public GithubConnectionController(UserGithubConnectionService userConnectionService, WorkspaceGithubLinkService workspaceLinkService,
            WorkspaceGithubAppInstallationService appInstallationService,
            WorkspaceGithubAppInstallationFlowService appInstallationFlowService,
            WorkspaceGithubAppRepositoryCatalogService repositoryCatalogService) {
        this.userConnectionService = userConnectionService;
        this.workspaceLinkService = workspaceLinkService;
        this.appInstallationService = appInstallationService;
        this.appInstallationFlowService = appInstallationFlowService;
        this.repositoryCatalogService = repositoryCatalogService;
    }

    @GetMapping("/api/account/integrations/github")
    public UserGithubConnectionResponse getMyConnection(@AuthenticationPrincipal No8doUserDetails currentUser) {
        return userConnectionService.get(currentUser.user().getId());
    }

    @GetMapping("/api/account/integrations/github/connect")
    public void connectMyGithub(@AuthenticationPrincipal No8doUserDetails currentUser, HttpServletResponse response) throws IOException {
        response.sendRedirect(userConnectionService.startConnection(currentUser.user().getId()));
    }

    @DeleteMapping("/api/account/integrations/github")
    public ResponseEntity<Void> disconnectMyGithub(@AuthenticationPrincipal No8doUserDetails currentUser) {
        userConnectionService.disconnect(currentUser.user().getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/account/integrations/github/callback")
    public void callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
            @RequestParam(required = false) String error, @AuthenticationPrincipal No8doUserDetails currentUser,
            HttpServletResponse response) throws IOException {
        if (error != null || currentUser == null) {
            response.sendRedirect(userConnectionService.errorUrl());
            return;
        }
        try {
            response.sendRedirect(userConnectionService.finishConnection(code, state, currentUser.user().getId()));
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() >= HttpStatus.INTERNAL_SERVER_ERROR.value()) throw ex;
            response.sendRedirect(userConnectionService.errorUrl());
        }
    }

    @GetMapping("/api/workspaces/{workspaceId}/integrations/github")
    public WorkspaceGithubLinkResponse getWorkspaceLink(@PathVariable UUID workspaceId, @AuthenticationPrincipal No8doUserDetails currentUser) {
        return workspaceLinkService.get(workspaceId, currentUser.user().getId());
    }

    @GetMapping("/api/workspaces/{workspaceId}/integrations/github/app-installation")
    public WorkspaceGithubAppInstallationResponse getWorkspaceAppInstallation(@PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        return appInstallationService.get(workspaceId, currentUser.user().getId());
    }

    @DeleteMapping("/api/workspaces/{workspaceId}/integrations/github/app-installation")
    public ResponseEntity<Void> disconnectWorkspaceAppInstallation(@PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        appInstallationService.disconnect(workspaceId, currentUser.user().getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/workspaces/{workspaceId}/integrations/github/app")
    public WorkspaceGithubAppStatusResponse getWorkspaceAppStatus(@PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        return appInstallationService.getStatus(workspaceId, currentUser.user().getId());
    }

    @GetMapping("/api/workspaces/{workspaceId}/integrations/github/app/repositories")
    public GithubAppRepositoryPageResponse listWorkspaceGithubAppRepositories(@PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "30") int perPage,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        return repositoryCatalogService.list(workspaceId, currentUser.user().getId(), page, perPage);
    }

    @GetMapping("/api/workspaces/{workspaceId}/integrations/github/app/repositories/{repositoryId}/preview")
    public GithubAppRepositoryPreviewResponse previewWorkspaceGithubAppRepository(@PathVariable UUID workspaceId,
            @PathVariable long repositoryId, @AuthenticationPrincipal No8doUserDetails currentUser) {
        return repositoryCatalogService.preview(workspaceId, currentUser.user().getId(), repositoryId);
    }

    @GetMapping("/api/workspaces/{workspaceId}/integrations/github-app/install")
    public void installWorkspaceGithubApp(@PathVariable UUID workspaceId, @AuthenticationPrincipal No8doUserDetails currentUser,
            HttpServletResponse response) throws IOException {
        response.sendRedirect(appInstallationFlowService.start(workspaceId, currentUser.user().getId()));
    }

    @GetMapping("/api/account/integrations/github/app/callback")
    public void workspaceGithubAppCallback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
            @RequestParam(name = "installation_id", required = false) Long installationId,
            @RequestParam(required = false) String error, @AuthenticationPrincipal No8doUserDetails currentUser,
            HttpServletResponse response) throws IOException {
        if (error != null || currentUser == null) {
            response.sendRedirect(appInstallationFlowService.errorUrl());
            return;
        }
        try {
            response.sendRedirect(appInstallationFlowService.finish(code, state, installationId, currentUser.user().getId()));
        } catch (WorkspaceGithubAppInstallationCallbackException exception) {
            response.sendRedirect(appInstallationFlowService.settingsErrorUrl(exception.getWorkspaceId()));
        } catch (ResponseStatusException exception) {
            response.sendRedirect(appInstallationFlowService.errorUrl());
        }
    }

    @PostMapping("/api/workspaces/{workspaceId}/integrations/github/link")
    public WorkspaceGithubLinkResponse linkMyGithub(@PathVariable UUID workspaceId, @AuthenticationPrincipal No8doUserDetails currentUser) {
        return workspaceLinkService.linkMyGithub(workspaceId, currentUser.user().getId());
    }

    @DeleteMapping("/api/workspaces/{workspaceId}/integrations/github")
    public void unlinkWorkspaceGithub(@PathVariable UUID workspaceId, @AuthenticationPrincipal No8doUserDetails currentUser) {
        workspaceLinkService.unlink(workspaceId, currentUser.user().getId());
    }
}
