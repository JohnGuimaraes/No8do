package com.no8do.api.workspace;

import com.no8do.api.auth.No8doUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;

    public WorkspaceController(
            WorkspaceService workspaceService,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService
    ) {
        this.workspaceService = workspaceService;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
    }
    @GetMapping("/{workspaceId}/members/public")
    public List<WorkspaceMemberResponse> publicMembers(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUser.user().getId());
        return workspaceMemberRepository.findByWorkspaceIdOrderByUserNameAsc(workspaceId)
            .stream()
            .map(WorkspaceMemberResponse::from)
            .toList();
    }

    @GetMapping("/{workspaceId}/members")
    public List<WorkspaceMemberManagementResponse> members(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        workspaceAuthorizationService.requireWorkspaceManager(workspaceId, currentUser.user().getId());
        return workspaceMemberRepository.findByWorkspaceIdOrderByUserNameAsc(workspaceId).stream()
            .map(WorkspaceMemberManagementResponse::from).toList();
    }

    @PatchMapping("/{workspaceId}/members/{userId}")
    public WorkspaceMemberManagementResponse updateMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal No8doUserDetails currentUser,
            @Valid @RequestBody UpdateWorkspaceMemberRequest request
    ) {
        WorkspaceMember actor = workspaceAuthorizationService.requireWorkspaceManager(workspaceId, currentUser.user().getId());
        WorkspaceMember target = workspaceAuthorizationService.requireWorkspaceMember(workspaceId, userId);
        workspaceAuthorizationService.requireCanManageMember(actor, target, request.role());
        target.setRole(request.role());
        return WorkspaceMemberManagementResponse.from(workspaceMemberRepository.save(target));
    }

    @DeleteMapping("/{workspaceId}/members/{userId}")
    public void removeMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        WorkspaceMember actor = workspaceAuthorizationService.requireWorkspaceManager(workspaceId, currentUser.user().getId());
        WorkspaceMember target = workspaceAuthorizationService.requireWorkspaceMember(workspaceId, userId);
        workspaceAuthorizationService.requireCanManageMember(actor, target, WorkspaceRole.VIEWER);
        workspaceMemberRepository.delete(target);
    }

    @GetMapping
    public List<WorkspaceResponse> list(@AuthenticationPrincipal No8doUserDetails currentUser) {
        return workspaceService.listForUser(currentUser.user().getId());
    }

    @PostMapping
    public WorkspaceResponse create(
            @AuthenticationPrincipal No8doUserDetails currentUser,
            @Valid @RequestBody CreateWorkspaceRequest request
    ) {
        return workspaceService.create(currentUser.user().getId(), request);
    }

    @DeleteMapping("/{workspaceId}")
    public void delete(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails currentUser,
            @Valid @RequestBody DeleteWorkspaceRequest request
    ) {
        workspaceService.delete(workspaceId, currentUser.user().getId(), request);
    }
}
