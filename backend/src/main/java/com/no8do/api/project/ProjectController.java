package com.no8do.api.project;

import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.github.ProjectGithubRepositoryResponse;
import com.no8do.api.github.ProjectGithubRepositoryService;
import com.no8do.api.github.UpdateProjectGithubRepositoryRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectGithubRepositoryService projectGithubRepositoryService;

    public ProjectController(ProjectService projectService, ProjectGithubRepositoryService projectGithubRepositoryService) {
        this.projectService = projectService;
        this.projectGithubRepositoryService = projectGithubRepositoryService;
    }

    @GetMapping
    public List<ProjectResponse> list(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "false") boolean archived,
            @AuthenticationPrincipal No8doUserDetails currentUser
    ) {
        return projectService.listByWorkspace(workspaceId, currentUser.user().getId(), archived);
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

    @PostMapping("/{projectId}/archive")
    public ProjectResponse archive(@PathVariable UUID workspaceId, @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        return projectService.archive(workspaceId, projectId, currentUser.user().getId());
    }

    @PostMapping("/{projectId}/restore")
    public ProjectResponse restore(@PathVariable UUID workspaceId, @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        return projectService.restore(workspaceId, projectId, currentUser.user().getId());
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@PathVariable UUID workspaceId, @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        projectService.delete(workspaceId, projectId, currentUser.user().getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{projectId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProjectResponse uploadCover(@PathVariable UUID workspaceId, @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser, @RequestParam("file") MultipartFile file) {
        return projectService.uploadCover(workspaceId, projectId, currentUser.user().getId(), file);
    }

    @GetMapping("/{projectId}/cover")
    public ResponseEntity<byte[]> getCover(@PathVariable UUID workspaceId, @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        ProjectCover cover = projectService.getCover(workspaceId, projectId, currentUser.user().getId());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(cover.contentType()))
            .cacheControl(CacheControl.noStore()).body(cover.bytes());
    }

    @DeleteMapping("/{projectId}/cover")
    public ProjectResponse deleteCover(@PathVariable UUID workspaceId, @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        return projectService.deleteCover(workspaceId, projectId, currentUser.user().getId());
    }

    @GetMapping("/{projectId}/github-repository")
    public ProjectGithubRepositoryResponse getGithubRepository(@PathVariable UUID workspaceId, @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        return projectGithubRepositoryService.get(workspaceId, projectId, currentUser.user().getId());
    }

    @PutMapping("/{projectId}/github-repository")
    public ProjectGithubRepositoryResponse associateGithubRepository(@PathVariable UUID workspaceId, @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser, @Valid @RequestBody UpdateProjectGithubRepositoryRequest request) {
        return projectGithubRepositoryService.associate(workspaceId, projectId, currentUser.user().getId(), request.repositoryId());
    }

    @DeleteMapping("/{projectId}/github-repository")
    public ResponseEntity<Void> dissociateGithubRepository(@PathVariable UUID workspaceId, @PathVariable UUID projectId,
            @AuthenticationPrincipal No8doUserDetails currentUser) {
        projectGithubRepositoryService.dissociate(workspaceId, projectId, currentUser.user().getId());
        return ResponseEntity.noContent().build();
    }
}
