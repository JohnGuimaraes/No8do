package com.no8do.api.workspace;

import com.no8do.api.auth.No8doUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
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
