package com.no8do.api.idea;

import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.project.ProjectResponse;
import com.no8do.api.project.ProjectStatus;
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
public class IdeaService {

    private static final int MAX_TITLE_LENGTH = 180;
    private static final int MAX_DESCRIPTION_LENGTH = 10000;

    private final IdeaRepository ideaRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;
    private final WorkspaceRepository workspaceRepository;

    public IdeaService(
            IdeaRepository ideaRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService,
            WorkspaceRepository workspaceRepository
    ) {
        this.ideaRepository = ideaRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional(readOnly = true)
    public List<IdeaResponse> list(UUID workspaceId, UUID currentUserId) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        return ideaRepository.findByWorkspaceIdOrderByUpdatedAtDesc(workspaceId)
            .stream()
            .map(IdeaResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public IdeaResponse get(UUID workspaceId, UUID ideaId, UUID currentUserId) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        return ideaRepository.findByIdAndWorkspaceId(ideaId, workspaceId)
            .map(IdeaResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Idea not found"));
    }

    @Transactional
    public IdeaResponse create(UUID workspaceId, UUID currentUserId, IdeaRequest request) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        Idea idea = new Idea(
            workspaceRepository.getReferenceById(workspaceId),
            normalizeRequiredTitle(request.title()),
            normalizeType(request.type()),
            userRepository.getReferenceById(currentUserId)
        );
        idea.setDescription(normalizeOptionalDescription(request.description()));
        return IdeaResponse.from(ideaRepository.save(idea));
    }

    @Transactional
    public IdeaResponse update(UUID workspaceId, UUID ideaId, UUID currentUserId, IdeaRequest request) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        Idea idea = ideaRepository.findByIdAndWorkspaceId(ideaId, workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Idea not found"));
        IdeaStatus status = normalizePatchStatus(request.status(), idea.getStatus());

        idea.setTitle(normalizeRequiredTitle(request.title()));
        idea.setDescription(normalizeOptionalDescription(request.description()));
        idea.setType(normalizeType(request.type()));
        idea.setStatus(status);

        return IdeaResponse.from(ideaRepository.saveAndFlush(idea));
    }

    @Transactional
    public IdeaConvertResponse convertToProject(UUID workspaceId, UUID ideaId, UUID currentUserId) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        Idea idea = ideaRepository.findByIdAndWorkspaceId(ideaId, workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Idea not found"));

        if (idea.getConvertedProject() != null || idea.getStatus() == IdeaStatus.CONVERTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idea already converted");
        }

        Project project = new Project(
            workspaceRepository.getReferenceById(workspaceId),
            idea.getTitle(),
            userRepository.getReferenceById(currentUserId)
        );
        project.setDescription(idea.getDescription());
        project.setStatus(ProjectStatus.IDEA);
        Project savedProject = projectRepository.saveAndFlush(project);

        idea.setConvertedProject(savedProject);
        idea.setStatus(IdeaStatus.CONVERTED);
        Idea savedIdea = ideaRepository.saveAndFlush(idea);

        return new IdeaConvertResponse(IdeaResponse.from(savedIdea), ProjectResponse.from(savedProject));
    }

    private IdeaType normalizeType(String type) {
        if (type == null || type.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idea type is required");
        }
        try {
            return IdeaType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idea type is invalid");
        }
    }

    private IdeaStatus normalizePatchStatus(String status, IdeaStatus currentStatus) {
        if (status == null || status.trim().isEmpty()) {
            return currentStatus;
        }
        IdeaStatus normalizedStatus;
        try {
            normalizedStatus = IdeaStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idea status is invalid");
        }
        if (normalizedStatus == IdeaStatus.CONVERTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idea cannot be marked as converted directly");
        }
        return normalizedStatus;
    }

    private String normalizeRequiredTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idea title is required");
        }
        String normalizedTitle = title.trim();
        if (normalizedTitle.length() > MAX_TITLE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idea title is too long");
        }
        return normalizedTitle;
    }

    private String normalizeOptionalDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return null;
        }
        String normalizedDescription = description.trim();
        if (normalizedDescription.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idea description is too long");
        }
        return normalizedDescription;
    }
}
