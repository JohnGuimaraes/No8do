package com.no8do.api.replay;

import com.no8do.api.project.Project;
import com.no8do.api.user.User;
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
@Table(name = "replay_usages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplayUsage {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "replay_id", nullable = false)
    private Replay replay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by")
    private User usedBy;

    @Column(name = "replay_version", nullable = false)
    private int replayVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReplayUsageResult result;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReplayUsageSource source;

    @Column(columnDefinition = "text")
    private String context;

    @Column(name = "used_at", nullable = false, updatable = false)
    private Instant usedAt;

    public ReplayUsage(Replay replay, Project project, User usedBy, int replayVersion,
            ReplayUsageResult result, ReplayUsageSource source, String context) {
        this.replay = replay;
        this.project = project;
        this.usedBy = usedBy;
        this.replayVersion = replayVersion;
        this.result = result;
        this.source = source;
        this.context = context;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (usedAt == null) usedAt = Instant.now();
    }
}
