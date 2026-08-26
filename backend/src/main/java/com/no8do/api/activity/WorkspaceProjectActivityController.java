package com.no8do.api.activity;

import com.no8do.api.auth.No8doUserDetails;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/activities")
public class WorkspaceProjectActivityController {

    private final ProjectActivityService projectActivityService;

    public WorkspaceProjectActivityController(ProjectActivityService projectActivityService) {
        this.projectActivityService = projectActivityService;
    }

    @GetMapping
    public List<WorkspaceProjectActivityResponse> list(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        return projectActivityService.listRecentByWorkspace(workspaceId, currentUser.user().getId(), limit);
    }
}
