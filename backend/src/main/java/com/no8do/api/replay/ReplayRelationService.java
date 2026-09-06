package com.no8do.api.replay;

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
public class ReplayRelationService {
    private final ReplayRepository replayRepository; private final ReplayRelationRepository relationRepository;
    private final WorkspaceRepository workspaceRepository; private final UserRepository userRepository; private final WorkspaceAuthorizationService authorization;
    public ReplayRelationService(ReplayRepository replayRepository, ReplayRelationRepository relationRepository, WorkspaceRepository workspaceRepository, UserRepository userRepository, WorkspaceAuthorizationService authorization) {
        this.replayRepository = replayRepository; this.relationRepository = relationRepository; this.workspaceRepository = workspaceRepository; this.userRepository = userRepository; this.authorization = authorization;
    }
    @Transactional(readOnly = true)
    public List<ReplayRelationResponse> list(UUID workspaceId, UUID replayId, UUID userId) {
        authorization.requireWorkspaceMember(workspaceId, userId); Replay replay = findReplay(workspaceId, replayId);
        return relationRepository.findByWorkspaceIdAndSourceReplayIdOrWorkspaceIdAndTargetReplayIdOrderByCreatedAtDesc(workspaceId, replayId, workspaceId, replayId).stream().map(relation -> ReplayRelationResponse.from(relation, replay)).toList();
    }
    @Transactional
    public ReplayRelationResponse create(UUID workspaceId, UUID sourceReplayId, UUID userId, CreateReplayRelationRequest request) {
        authorization.requireWorkspaceWrite(workspaceId, userId); Replay source = findReplay(workspaceId, sourceReplayId); Replay target = findReplay(workspaceId, request.targetReplayId());
        if (source.getId().equals(target.getId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A Replay cannot relate to itself");
        if (request.type() == ReplayRelationType.RELATED_TO && source.getId().compareTo(target.getId()) > 0) { Replay swap = source; source = target; target = swap; }
        if (relationRepository.existsByWorkspaceIdAndSourceReplayIdAndTargetReplayIdAndType(workspaceId, source.getId(), target.getId(), request.type())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Replay relation already exists");
        ReplayRelation saved = relationRepository.saveAndFlush(new ReplayRelation(workspaceRepository.getReferenceById(workspaceId), source, target, request.type(), userRepository.getReferenceById(userId)));
        return ReplayRelationResponse.from(saved, findReplay(workspaceId, sourceReplayId));
    }
    @Transactional
    public void delete(UUID workspaceId, UUID replayId, UUID relationId, UUID userId) {
        authorization.requireWorkspaceWrite(workspaceId, userId); findReplay(workspaceId, replayId);
        ReplayRelation relation = relationRepository.findByIdAndWorkspaceId(relationId, workspaceId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Replay relation not found"));
        if (!relation.getSourceReplay().getId().equals(replayId) && !relation.getTargetReplay().getId().equals(replayId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Replay relation not found");
        relationRepository.delete(relation);
    }
    private Replay findReplay(UUID workspaceId, UUID replayId) { return replayRepository.findByIdAndWorkspaceId(replayId, workspaceId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Replay not found")); }
}
