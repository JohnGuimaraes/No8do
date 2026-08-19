package com.no8do.api.credential;

import com.no8do.api.auth.No8doUserDetails;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectId}/credentials")
public class ProjectCredentialController {

    private final ProjectCredentialService projectCredentialService;

    public ProjectCredentialController(ProjectCredentialService projectCredentialService) {
        this.projectCredentialService = projectCredentialService;
    }

    @GetMapping
    public List<ProjectCredentialResponse> list(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        return projectCredentialService.list(workspaceId, projectId, currentUser.user().getId());
    }

    @PostMapping
    public ProjectCredentialResponse create(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser,
            @RequestBody CreateProjectCredentialRequest request
    ) {
        return projectCredentialService.create(workspaceId, projectId, currentUser.user().getId(), request);
    }

    @PostMapping("/{credentialId}/reveal")
    public ResponseEntity<RevealProjectCredentialResponse> reveal(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID credentialId,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(projectCredentialService.reveal(workspaceId, projectId, credentialId, currentUser.user().getId()));
    }
}
