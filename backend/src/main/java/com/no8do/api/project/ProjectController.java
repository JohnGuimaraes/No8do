package com.no8do.api.project;

import com.no8do.api.auth.No8doUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectResponse> list(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        return projectService.listByWorkspace(workspaceId, currentUser.user().getId());
    }

    @PostMapping
    public ProjectResponse create(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails currentUser,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        return projectService.create(workspaceId, currentUser.user().getId(), request);
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        return projectService.getByWorkspace(workspaceId, projectId, currentUser.user().getId());
    }

    @PatchMapping("/{projectId}")
    public ProjectResponse update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        return projectService.update(workspaceId, projectId, currentUser.user().getId(), request);
    }
}
