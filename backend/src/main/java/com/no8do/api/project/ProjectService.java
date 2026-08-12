package com.no8do.api.project;

import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import com.no8do.api.workspace.WorkspaceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final WorkspaceRepository workspaceRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            UserRepository userRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService,
            WorkspaceRepository workspaceRepository
    ) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listByWorkspace(UUID workspaceId, UUID currentUserId) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        return projectRepository.findByWorkspaceId(workspaceId)
            .stream()
            .map(ProjectResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getByWorkspace(UUID workspaceId, UUID projectId, UUID currentUserId) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        return projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
            .map(ProjectResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    @Transactional
    public ProjectResponse create(UUID workspaceId, UUID currentUserId, CreateProjectRequest request) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        Project project = new Project(
            workspaceRepository.getReferenceById(workspaceId),
            normalizeRequiredName(request.name()),
            userRepository.getReferenceById(currentUserId)
        );
        project.setDescription(request.description());
        project.setCurrentState(request.currentState());
        if (request.status() != null) {
            project.setStatus(request.status());
        }
        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional
    public ProjectResponse update(UUID workspaceId, UUID projectId, UUID currentUserId, UpdateProjectRequest request) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        project.setName(normalizeRequiredName(request.name()));
        project.setDescription(request.description());
        if (request.status() != null) {
            project.setStatus(request.status());
        }
        project.setCurrentState(request.currentState());

        return ProjectResponse.from(project);
    }

    private String normalizeRequiredName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project name is required");
        }
        return name.trim();
    }
}
