package com.no8do.api.workitem;

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
@RequestMapping("/api/workspaces/{workspaceId}/work-items")
public class WorkspaceWorkItemController {

    private final ProjectWorkItemService projectWorkItemService;

    public WorkspaceWorkItemController(ProjectWorkItemService projectWorkItemService) {
        this.projectWorkItemService = projectWorkItemService;
    }

    @GetMapping
    public List<WorkspaceWorkItemResponse> list(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal No8doUserDetails currentUser,
            @RequestParam(required = false) ProjectWorkItemStatus status,
            @RequestParam(required = false) ProjectWorkItemType type
    ) {
        return projectWorkItemService.listByWorkspace(workspaceId, currentUser.user().getId(), status, type);
    }
}
