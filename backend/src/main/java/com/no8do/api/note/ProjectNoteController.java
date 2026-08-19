package com.no8do.api.note;

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
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectId}/notes")
public class ProjectNoteController {

    private final ProjectNoteService projectNoteService;

    public ProjectNoteController(ProjectNoteService projectNoteService) {
        this.projectNoteService = projectNoteService;
    }

    @GetMapping
    public List<ProjectNoteResponse> list(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        return projectNoteService.list(workspaceId, projectId, currentUser.user().getId());
    }

    @PostMapping
    public ProjectNoteResponse create(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser,
            @RequestBody CreateProjectNoteRequest request
    ) {
        return projectNoteService.create(workspaceId, projectId, currentUser.user().getId(), request);
    }
}
