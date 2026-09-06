package com.no8do.api.replay;

import java.time.Instant;
import java.util.UUID;

public record ReplayRelationResponse(UUID id, ReplayRelationType type, ReplayRelationDirection direction,
        UUID relatedReplayId, String relatedReplayTitle, ReplayType relatedReplayType,
        ReplayStatus relatedReplayStatus, int relatedReplayVersion, Instant createdAt) {
    static ReplayRelationResponse from(ReplayRelation relation, Replay replay) {
        Replay related = relation.getSourceReplay().getId().equals(replay.getId()) ? relation.getTargetReplay() : relation.getSourceReplay();
        ReplayRelationDirection direction = relation.getType() == ReplayRelationType.RELATED_TO ? ReplayRelationDirection.RELATED
            : relation.getSourceReplay().getId().equals(replay.getId()) ? ReplayRelationDirection.OUTGOING : ReplayRelationDirection.INCOMING;
        return new ReplayRelationResponse(relation.getId(), relation.getType(), direction, related.getId(), related.getTitle(), related.getType(), related.getStatus(), related.getVersion(), relation.getCreatedAt());
    }
}
