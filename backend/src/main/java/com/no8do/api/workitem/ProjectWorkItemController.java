package com.no8do.api.workitem;

import com.no8do.api.auth.No8doUserDetails;
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
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectId}/work-items")
public class ProjectWorkItemController {

    private final ProjectWorkItemService projectWorkItemService;

    public ProjectWorkItemController(ProjectWorkItemService projectWorkItemService) {
        this.projectWorkItemService = projectWorkItemService;
    }

    @GetMapping
    public List<ProjectWorkItemResponse> list(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        return projectWorkItemService.list(workspaceId, projectId, currentUser.user().getId());
    }

    @PostMapping
    public ProjectWorkItemResponse create(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser,
            @RequestBody CreateProjectWorkItemRequest request
    ) {
        return projectWorkItemService.create(workspaceId, projectId, currentUser.user().getId(), request);
    }

    @PatchMapping("/{workItemId}")
    public ProjectWorkItemResponse updateStatus(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID workItemId,
            @AuthenticationPrincipal No8doUserDetails currentUser,
            @RequestBody UpdateProjectWorkItemStatusRequest request
    ) {
        return projectWorkItemService.updateStatus(
            workspaceId,
            projectId,
            workItemId,
            currentUser.user().getId(),
            request
        );
    }
}
