package com.no8do.api.workitem;

import com.no8do.api.project.ProjectRepository;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectWorkItemService {

    private static final int MAX_TITLE_LENGTH = 180;
    private static final int MAX_DETAILS_LENGTH = 2_000;

    private final ProjectWorkItemRepository projectWorkItemRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;

    public ProjectWorkItemService(
            ProjectWorkItemRepository projectWorkItemRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService
    ) {
        this.projectWorkItemRepository = projectWorkItemRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
    }

    @Transactional(readOnly = true)
    public List<ProjectWorkItemResponse> list(UUID workspaceId, UUID projectId, UUID currentUserId) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        return projectWorkItemRepository.findByProjectIdForProjectView(projectId)
            .stream()
            .map(ProjectWorkItemResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceWorkItemResponse> listByWorkspace(
            UUID workspaceId,
            UUID currentUserId,
            ProjectWorkItemStatus status,
            ProjectWorkItemType type
    ) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        return projectWorkItemRepository.findByWorkspaceIdForWorkspaceView(workspaceId, status, type)
            .stream()
            .map(WorkspaceWorkItemResponse::from)
            .toList();
    }

    @Transactional
    public ProjectWorkItemResponse create(
            UUID workspaceId,
            UUID projectId,
            UUID currentUserId,
            CreateProjectWorkItemRequest request
    ) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        ProjectWorkItem item = new ProjectWorkItem(
            projectRepository.getReferenceById(projectId),
            normalizeRequiredType(request.type()),
            normalizeRequiredTitle(request.title()),
            normalizeOptionalDetails(request.details()),
            userRepository.getReferenceById(currentUserId)
        );
        return ProjectWorkItemResponse.from(projectWorkItemRepository.save(item));
    }

    @Transactional
    public ProjectWorkItemResponse updateStatus(
            UUID workspaceId,
            UUID projectId,
            UUID workItemId,
            UUID currentUserId,
            UpdateProjectWorkItemStatusRequest request
    ) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        ProjectWorkItem item = projectWorkItemRepository.findByIdAndProjectId(workItemId, projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found"));
        ProjectWorkItemStatus status = normalizeRequiredStatus(request.status());
        item.setStatus(status);
        item.setCompletedAt(status == ProjectWorkItemStatus.DONE ? Instant.now() : null);
        return ProjectWorkItemResponse.from(projectWorkItemRepository.saveAndFlush(item));
    }

    private ProjectWorkItemType normalizeRequiredType(ProjectWorkItemType type) {
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work item type is required");
        }
        return type;
    }

    private ProjectWorkItemStatus normalizeRequiredStatus(ProjectWorkItemStatus status) {
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work item status is required");
        }
        return status;
    }

    private String normalizeRequiredTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work item title is required");
        }
        String normalizedTitle = title.trim();
        if (normalizedTitle.length() > MAX_TITLE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work item title is too long");
        }
        return normalizedTitle;
    }

    private String normalizeOptionalDetails(String details) {
        if (details == null || details.trim().isEmpty()) {
            return null;
        }
        String normalizedDetails = details.trim();
        if (normalizedDetails.length() > MAX_DETAILS_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work item details are too long");
        }
        return normalizedDetails;
    }
}
