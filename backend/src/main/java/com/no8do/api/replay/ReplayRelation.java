package com.no8do.api.replay;

import com.no8do.api.user.User;
import com.no8do.api.workspace.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "replay_relations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplayRelation {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "workspace_id", nullable = false) private Workspace workspace;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "source_replay_id", nullable = false) private Replay sourceReplay;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "target_replay_id", nullable = false) private Replay targetReplay;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ReplayRelationType type;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by") private User createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public ReplayRelation(Workspace workspace, Replay sourceReplay, Replay targetReplay, ReplayRelationType type, User createdBy) {
        this.workspace = workspace; this.sourceReplay = sourceReplay; this.targetReplay = targetReplay; this.type = type; this.createdBy = createdBy;
    }
    @PrePersist void prePersist() { if (id == null) id = UUID.randomUUID(); if (createdAt == null) createdAt = Instant.now(); }
}
