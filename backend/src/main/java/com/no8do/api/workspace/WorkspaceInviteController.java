package com.no8do.api.workspace;

import com.no8do.api.auth.No8doUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkspaceInviteController {
    private final WorkspaceInviteService inviteService;
    public WorkspaceInviteController(WorkspaceInviteService inviteService) { this.inviteService = inviteService; }

    @PostMapping("/api/workspaces/{workspaceId}/invites")
    public WorkspaceInviteResponse create(@PathVariable UUID workspaceId, @AuthenticationPrincipal No8doUserDetails user, @Valid @RequestBody CreateWorkspaceInviteRequest request) { return inviteService.create(workspaceId, user.user().getId(), request); }
    @GetMapping("/api/workspaces/{workspaceId}/invites")
    public List<WorkspaceInviteResponse> list(@PathVariable UUID workspaceId, @AuthenticationPrincipal No8doUserDetails user) { return inviteService.list(workspaceId, user.user().getId()); }
    @DeleteMapping("/api/workspaces/{workspaceId}/invites/{inviteId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID workspaceId, @PathVariable UUID inviteId, @AuthenticationPrincipal No8doUserDetails user) { inviteService.revoke(workspaceId, inviteId, user.user().getId()); return ResponseEntity.noContent().build(); }
    @GetMapping("/api/workspace-invites/{token}")
    public WorkspaceInvitePublicResponse get(@PathVariable String token) { return inviteService.getPublic(token); }
    @PostMapping("/api/workspace-invites/{token}/accept")
    public WorkspaceInviteAcceptanceResponse accept(@PathVariable String token, @AuthenticationPrincipal No8doUserDetails user) { return inviteService.accept(token, user.user().getId()); }
    @PostMapping("/api/workspace-invites/{token}/register")
    public WorkspaceInviteService.WorkspaceInviteRegistrationResponse register(@PathVariable String token, @Valid @RequestBody RegisterWorkspaceInviteRequest request, HttpServletRequest httpRequest) { return inviteService.register(token, request, httpRequest); }
}
