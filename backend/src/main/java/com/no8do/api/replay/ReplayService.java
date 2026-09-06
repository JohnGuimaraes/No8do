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
import java.util.Comparator;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReplayService {

    private static final int MAX_TITLE_LENGTH = 180;

    private final ReplayRepository replayRepository;
    private final ReplayUsageRepository replayUsageRepository;
    private final ReplayVersionRepository replayVersionRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceAuthorizationService workspaceAuthorizationService;

    public ReplayService(
            ReplayRepository replayRepository,
            ReplayUsageRepository replayUsageRepository,
            ReplayVersionRepository replayVersionRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceAuthorizationService workspaceAuthorizationService
    ) {
        this.replayRepository = replayRepository;
        this.replayUsageRepository = replayUsageRepository;
        this.replayVersionRepository = replayVersionRepository;
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
        Replay saved = replayRepository.saveAndFlush(replay);
        replayVersionRepository.save(new ReplayVersion(saved, userRepository.getReferenceById(currentUserId)));
        return ReplayResponse.from(saved);
    }

    @Transactional
    public ReplayResponse update(UUID workspaceId, UUID replayId, UUID currentUserId, UpdateReplayRequest request) {
        workspaceAuthorizationService.requireWorkspaceWrite(workspaceId, currentUserId);
        Replay replay = find(workspaceId, replayId);

        String title = request.hasTitle() ? normalizeRequiredTitle(request.title()) : replay.getTitle();
        ReplayType type = request.hasType() ? request.type() : replay.getType();
        String problem = request.hasProblem() ? normalizeOptionalText(request.problem()) : replay.getProblem();
        String solution = request.hasSolution() ? normalizeOptionalText(request.solution()) : replay.getSolution();
        String context = request.hasContext() ? normalizeOptionalText(request.context()) : replay.getContext();
        String[] tags = request.hasTags() ? normalizeTerms(request.tags()) : replay.getTags();
        String[] stack = request.hasStack() ? normalizeTerms(request.stack()) : replay.getStack();
        ReplayStatus status = request.hasStatus() ? request.status() : replay.getStatus();
        Project project = request.hasProjectId() ? resolveProject(workspaceId, request.projectId()) : replay.getProject();

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
        Replay saved = replayRepository.saveAndFlush(replay);
        replayVersionRepository.save(new ReplayVersion(saved, userRepository.getReferenceById(currentUserId)));
        return ReplayResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ReplayResponse> search(UUID workspaceId, UUID currentUserId, String query) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        if (query == null || query.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Replay search query is required");
        }
        String normalizedQuery = query.trim();
        return replayRepository.searchByWorkspaceId(workspaceId, normalizedQuery).stream()
            .sorted(replayOrder(replay -> relevance(replay, normalizedQuery)))
            .map(ReplayResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SimilarReplayResponse> findSimilar(UUID workspaceId, UUID currentUserId, FindSimilarReplaysRequest request) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        return replayRepository.findByWorkspaceIdOrderByUpdatedAtDesc(workspaceId).stream()
            .map(replay -> new ScoredReplay(replay, similarity(replay, request)))
            .filter(candidate -> candidate.score() > 0)
            .sorted(Comparator.comparingInt(ScoredReplay::score).reversed()
                .thenComparing(Comparator.comparingInt((ScoredReplay candidate) -> candidate.replay().getUsageCount()).reversed())
                .thenComparing(candidate -> candidate.replay().getUpdatedAt(), Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.replay().getId()))
            .limit(5)
            .map(candidate -> SimilarReplayResponse.from(candidate.replay(), candidate.score())).toList();
    }

    @Transactional
    public ReplayUsageResponse registerUsage(UUID workspaceId, UUID replayId, UUID currentUserId, RegisterReplayUsageRequest request) {
        workspaceAuthorizationService.requireWorkspaceWrite(workspaceId, currentUserId);
        Replay replay = find(workspaceId, replayId);
        Project project = resolveProject(workspaceId, request.projectId());
        int replayVersion = request.replayVersion() == null ? replay.getVersion() : request.replayVersion();
        ReplayUsage usage = new ReplayUsage(
            replay,
            project,
            userRepository.getReferenceById(currentUserId),
            replayVersion,
            request.result(),
            request.source(),
            normalizeOptionalText(request.context())
        );
        replayUsageRepository.saveAndFlush(usage);
        ReplayUsageResponse response = ReplayUsageResponse.from(usage);
        int successIncrement = request.result() == ReplayUsageResult.SUCCESS ? 1 : 0;
        int failureIncrement = request.result() == ReplayUsageResult.FAILURE ? 1 : 0;
        replayRepository.incrementUsageMetrics(workspaceId, replayId, successIncrement, failureIncrement, usage.getUsedAt());
        return response;
    }

    @Transactional(readOnly = true)
    public List<ReplayUsageResponse> listUsages(UUID workspaceId, UUID replayId, UUID currentUserId) {
        workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId);
        find(workspaceId, replayId);
        return replayUsageRepository.findByReplayIdOrderByUsedAtDesc(replayId).stream().map(ReplayUsageResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ReplayVersionResponse> listVersions(UUID workspaceId, UUID replayId, UUID currentUserId) { workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId); find(workspaceId, replayId); return replayVersionRepository.findByReplayIdOrderByVersionDesc(replayId).stream().map(ReplayVersionResponse::from).toList(); }

    @Transactional(readOnly = true)
    public ReplayVersionResponse getVersion(UUID workspaceId, UUID replayId, int version, UUID currentUserId) { workspaceAuthorizationService.requireWorkspaceMember(workspaceId, currentUserId); find(workspaceId, replayId); return ReplayVersionResponse.from(replayVersionRepository.findByReplayIdAndVersion(replayId, version).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Replay version not found"))); }

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

    private Comparator<Replay> replayOrder(java.util.function.ToIntFunction<Replay> score) {
        return Comparator.comparingInt(score).reversed()
            .thenComparing(Comparator.comparingInt(Replay::getUsageCount).reversed())
            .thenComparing(Replay::getUpdatedAt, Comparator.reverseOrder())
            .thenComparing(Replay::getId);
    }

    private int similarity(Replay replay, FindSimilarReplaysRequest request) {
        int score = relevance(replay, request.query()) + scoreText(replay.getTitle(), request.title(), 100) + scoreText(replay.getProblem(), request.problem(), 80);
        for (String tag : safeTerms(request.tags())) score += scoreTerms(replay.getTags(), tag, 60);
        for (String stack : safeTerms(request.stack())) score += scoreTerms(replay.getStack(), stack, 50);
        if (request.type() != null && request.type() == replay.getType()) score += 10;
        return score;
    }

    private int relevance(Replay replay, String query) {
        return scoreText(replay.getTitle(), query, 100)
            + scoreText(replay.getProblem(), query, 80)
            + scoreTerms(replay.getTags(), query, 60)
            + scoreTerms(replay.getStack(), query, 50)
            + scoreText(replay.getSolution(), query, 30)
            + scoreText(replay.getContext(), query, 20);
    }

    private int scoreTerms(String[] values, String query, int weight) {
        return Arrays.stream(values == null ? new String[0] : values).mapToInt(value -> scoreText(value, query, weight)).sum();
    }

    private int scoreText(String value, String query, int weight) {
        if (value == null || query == null || query.trim().isEmpty()) return 0;
        String field = value.toLowerCase(java.util.Locale.ROOT);
        String term = query.trim().toLowerCase(java.util.Locale.ROOT);
        if (field.equals(term)) return weight * 10;
        if (field.startsWith(term)) return weight * 7;
        return field.contains(term) ? weight * 4 : 0;
    }

    private List<String> safeTerms(List<String> values) { return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList(); }

    private record ScoredReplay(Replay replay, int score) {}
}
