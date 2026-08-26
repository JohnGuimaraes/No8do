package com.no8do.api.workspace;

import com.no8do.api.auth.No8doUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    @GetMapping("/{workspaceId}/members")
    public List<WorkspaceMemberResponse> members(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUser.user().getId());
        return workspaceMemberRepository.findByWorkspaceIdOrderByUserNameAsc(workspaceId)
            .stream()
            .map(WorkspaceMemberResponse::from)
            .toList();
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
}
