package com.no8do.api.activity;

import com.no8do.api.project.ProjectRepository;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectActivityService {

    private static final int MAX_WORKSPACE_ACTIVITY_LIMIT = 30;

    private final ProjectActivityRepository projectActivityRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;

    public ProjectActivityService(
            ProjectActivityRepository projectActivityRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService
    ) {
        this.projectActivityRepository = projectActivityRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
    }

    @Transactional(readOnly = true)
    public List<ProjectActivityResponse> list(UUID workspaceId, UUID projectId, UUID currentUserId) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        return projectActivityRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
            .stream()
            .map(ProjectActivityResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceProjectActivityResponse> listRecentByWorkspace(
            UUID workspaceId,
            UUID currentUserId,
            int limit
    ) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        if (limit < 1 || limit > MAX_WORKSPACE_ACTIVITY_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Activity limit must be between 1 and 30");
        }

        return projectActivityRepository.findRecentByWorkspaceId(workspaceId, PageRequest.of(0, limit))
            .stream()
            .map(WorkspaceProjectActivityResponse::from)
            .toList();
    }

    @Transactional
    public ProjectActivityResponse create(
            UUID workspaceId,
            UUID projectId,
            UUID currentUserId,
            CreateProjectActivityRequest request
    ) {
        workspaceAuthorizationService.requireProjectWriteAccess(projectId, workspaceId, currentUserId);
        ProjectActivity activity = new ProjectActivity(
            projectRepository.getReferenceById(projectId),
            userRepository.getReferenceById(currentUserId),
            request.type() == null ? ProjectActivityType.UPDATE : request.type(),
            normalizeRequiredContent(request.content())
        );
        return ProjectActivityResponse.from(projectActivityRepository.save(activity));
    }

    private String normalizeRequiredContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Activity content is required");
        }
        return content.trim();
    }
}
