package com.no8do.api.note;

import com.no8do.api.project.ProjectRepository;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectNoteService {

    static final int MAX_CONTENT_LENGTH = 10_000;

    private final ProjectNoteRepository projectNoteRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;

    public ProjectNoteService(
            ProjectNoteRepository projectNoteRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService
    ) {
        this.projectNoteRepository = projectNoteRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
    }

    @Transactional(readOnly = true)
    public List<ProjectNoteResponse> list(UUID workspaceId, UUID projectId, UUID currentUserId) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        return projectNoteRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
            .stream()
            .map(ProjectNoteResponse::from)
            .toList();
    }

    @Transactional
    public ProjectNoteResponse create(
            UUID workspaceId,
            UUID projectId,
            UUID currentUserId,
            CreateProjectNoteRequest request
    ) {
        workspaceAuthorizationService.requireProjectAccess(projectId, workspaceId, currentUserId);
        ProjectNote note = new ProjectNote(
            projectRepository.getReferenceById(projectId),
            userRepository.getReferenceById(currentUserId),
            normalizeRequiredContent(request.content())
        );
        return ProjectNoteResponse.from(projectNoteRepository.save(note));
    }

    private String normalizeRequiredContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Note content is required");
        }
        String normalizedContent = content.trim();
        if (normalizedContent.length() > MAX_CONTENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Note content is too long");
        }
        return normalizedContent;
    }
}
