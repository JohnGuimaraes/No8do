package com.no8do.api.technicalinfo;

import com.no8do.api.auth.No8doUserDetails;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects/{projectId}/technical-info")
public class ProjectTechnicalInfoController {

    private final ProjectTechnicalInfoService projectTechnicalInfoService;

    public ProjectTechnicalInfoController(ProjectTechnicalInfoService projectTechnicalInfoService) {
        this.projectTechnicalInfoService = projectTechnicalInfoService;
    }

    @GetMapping
    public ProjectTechnicalInfoResponse get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        return projectTechnicalInfoService.get(workspaceId, projectId, currentUser.user().getId());
    }

    @PutMapping
    public ProjectTechnicalInfoResponse upsert(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser,
            @RequestBody ProjectTechnicalInfoRequest request
    ) {
        return projectTechnicalInfoService.upsert(workspaceId, projectId, currentUser.user().getId(), request);
    }
}
