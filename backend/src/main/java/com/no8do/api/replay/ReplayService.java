package com.no8do.api.replay;

import com.no8do.api.project.Project;
import com.no8do.api.project.ProjectRepository;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import com.no8do.api.workspace.WorkspaceRepository;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReplayService {

    private static final int MAX_TITLE_LENGTH = 180;

    private final ReplayRepository replayRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;

    public ReplayService(
            ReplayRepository replayRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService
    ) {
        this.replayRepository = replayRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceAuthorizationService = workspaceAuthorizationService;
    }

    @Transactional(readOnly = true)
    public List<ReplayResponse> list(UUID workspaceId, UUID currentUserId) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        return replayRepository.findByWorkspaceIdOrderByUpdatedAtDesc(workspaceId).stream().map(ReplayResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ReplayResponse get(UUID workspaceId, UUID replayId, UUID currentUserId) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        return ReplayResponse.from(find(workspaceId, replayId));
    }

    @Transactional
    public ReplayResponse create(UUID workspaceId, UUID currentUserId, CreateReplayRequest request) {
        workspaceAuthorizationService.requireWorkspaceWrite(workspaceId, currentUserId);
        Replay replay = new Replay(
            workspaceRepository.getReferenceById(workspaceId),
            resolveProject(workspaceId, request.projectId()),
            normalizeRequiredTitle(request.title()),
            request.type(),
            userRepository.getReferenceById(currentUserId)
        );
        replay.setProblem(normalizeOptionalText(request.problem()));
        replay.setSolution(normalizeOptionalText(request.solution()));
        replay.setContext(normalizeOptionalText(request.context()));
        replay.setTags(normalizeTerms(request.tags()));
        replay.setStack(normalizeTerms(request.stack()));
        replay.setStatus(request.status() == null ? ReplayStatus.DRAFT : request.status());
        return ReplayResponse.from(replayRepository.save(replay));
    }

    @Transactional
    public ReplayResponse update(UUID workspaceId, UUID replayId, UUID currentUserId, UpdateReplayRequest request) {
        workspaceAuthorizationService.requireWorkspaceWrite(workspaceId, currentUserId);
        Replay replay = find(workspaceId, replayId);

        String title = request.title() == null ? replay.getTitle() : normalizeRequiredTitle(request.title());
        ReplayType type = request.type() == null ? replay.getType() : request.type();
        String problem = normalizeOptionalText(request.problem());
        String solution = normalizeOptionalText(request.solution());
        String context = normalizeOptionalText(request.context());
        String[] tags = normalizeTerms(request.tags());
        String[] stack = normalizeTerms(request.stack());
        ReplayStatus status = request.status() == null ? replay.getStatus() : request.status();
        Project project = resolveProject(workspaceId, request.projectId());

        boolean changed = !Objects.equals(replay.getTitle(), title)
            || replay.getType() != type
            || !Objects.equals(replay.getProblem(), problem)
            || !Objects.equals(replay.getSolution(), solution)
            || !Objects.equals(replay.getContext(), context)
            || !Arrays.equals(replay.getTags(), tags)
            || !Arrays.equals(replay.getStack(), stack)
            || replay.getStatus() != status
            || !sameProject(replay.getProject(), project);
        if (!changed) return ReplayResponse.from(replay);

        replay.setTitle(title);
        replay.setType(type);
        replay.setProblem(problem);
        replay.setSolution(solution);
        replay.setContext(context);
        replay.setTags(tags);
        replay.setStack(stack);
        replay.setStatus(status);
        replay.setProject(project);
        replay.incrementVersion();
        return ReplayResponse.from(replayRepository.saveAndFlush(replay));
    }

    @Transactional(readOnly = true)
    public List<ReplayResponse> search(UUID workspaceId, UUID currentUserId, String query) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        if (query == null || query.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Replay search query is required");
        }
        return replayRepository.searchByWorkspaceId(workspaceId, query.trim()).stream().map(ReplayResponse::from).toList();
    }

    private Replay find(UUID workspaceId, UUID replayId) {
        return replayRepository.findByIdAndWorkspaceId(replayId, workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Replay not found"));
    }

    private Project resolveProject(UUID workspaceId, UUID projectId) {
        if (projectId == null) return null;
        return projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private boolean sameProject(Project left, Project right) {
        return left == null ? right == null : right != null && left.getId().equals(right.getId());
    }

    private String normalizeRequiredTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Replay title is required");
        }
        String normalized = title.trim();
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Replay title is too long");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String[] normalizeTerms(List<String> values) {
        if (values == null) return new String[0];
        return values.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
            .toArray(String[]::new);
    }
}
