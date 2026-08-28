package com.no8do.api.note;

import com.no8do.api.activity.ProjectActivity;
import com.no8do.api.activity.ProjectActivityRepository;
import com.no8do.api.activity.ProjectActivityType;
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
    private final ProjectActivityRepository projectActivityRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;

    public ProjectNoteService(
            ProjectNoteRepository projectNoteRepository,
            ProjectActivityRepository projectActivityRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService
    ) {
        this.projectNoteRepository = projectNoteRepository;
        this.projectActivityRepository = projectActivityRepository;
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
        workspaceAuthorizationService.requireProjectWriteAccess(projectId, workspaceId, currentUserId);
        ProjectNote note = new ProjectNote(
            projectRepository.getReferenceById(projectId),
            userRepository.getReferenceById(currentUserId),
            normalizeRequiredContent(request.content()), request.type()
        );
        ProjectNote savedNote = projectNoteRepository.save(note);
        projectActivityRepository.save(new ProjectActivity(
            savedNote.getProject(),
            userRepository.getReferenceById(currentUserId),
            ProjectActivityType.UPDATE,
            savedNote.getType() == ProjectNoteType.DECISION ? "Decisão registrada." : "Nota adicionada."
        ));
        return ProjectNoteResponse.from(savedNote);
    }

    @Transactional
    public ProjectNoteResponse update(UUID workspaceId, UUID projectId, UUID noteId, UUID currentUserId, UpdateProjectNoteRequest request) {
        workspaceAuthorizationService.requireProjectWriteAccess(projectId, workspaceId, currentUserId);
        ProjectNote note = find(projectId, noteId);
        String content = normalizeRequiredContent(request.content());
        ProjectNoteType type = request.type() == null ? ProjectNoteType.NOTE : request.type();
        if (note.getContent().equals(content) && note.getType() == type) return ProjectNoteResponse.from(note);
        boolean decisionCreated = note.getType() != ProjectNoteType.DECISION && type == ProjectNoteType.DECISION;
        note.setContent(content); note.setType(type);
        projectActivityRepository.save(new ProjectActivity(note.getProject(), userRepository.getReferenceById(currentUserId), ProjectActivityType.UPDATE, decisionCreated ? "Decisão registrada." : "Nota atualizada."));
        return ProjectNoteResponse.from(projectNoteRepository.saveAndFlush(note));
    }

    @Transactional
    public void delete(UUID workspaceId, UUID projectId, UUID noteId, UUID currentUserId) {
        workspaceAuthorizationService.requireProjectWriteAccess(projectId, workspaceId, currentUserId);
        ProjectNote note = find(projectId, noteId);
        projectNoteRepository.delete(note);
        projectActivityRepository.save(new ProjectActivity(note.getProject(), userRepository.getReferenceById(currentUserId), ProjectActivityType.UPDATE, "Nota removida."));
    }

    private ProjectNote find(UUID projectId, UUID noteId) { return projectNoteRepository.findByIdAndProjectId(noteId, projectId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found")); }

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
