package com.no8do.api.activity;

import com.no8do.api.auth.No8doUserDetails;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectId}/activities")
public class ProjectActivityController {

    private final ProjectActivityService projectActivityService;

    public ProjectActivityController(ProjectActivityService projectActivityService) {
        this.projectActivityService = projectActivityService;
    }

    @GetMapping
    public List<ProjectActivityResponse> list(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        return projectActivityService.list(workspaceId, projectId, currentUser.user().getId());
    }

    @PostMapping
    public ProjectActivityResponse create(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser,
            @RequestBody CreateProjectActivityRequest request
    ) {
        return projectActivityService.create(workspaceId, projectId, currentUser.user().getId(), request);
    }
}
