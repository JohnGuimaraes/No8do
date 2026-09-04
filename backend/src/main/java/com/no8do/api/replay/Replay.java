package com.no8do.api.replay;

import com.no8do.api.project.Project;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "replays")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Replay {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    @Setter
    private Project project;

    @Column(nullable = false, length = 180)
    @Setter
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Setter
    private ReplayType type;

    @Column(columnDefinition = "text")
    @Setter
    private String problem;

    @Column(columnDefinition = "text")
    @Setter
    private String solution;

    @Column(columnDefinition = "text")
    @Setter
    private String context;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    @Setter
    private String[] tags = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    @Setter
    private String[] stack = new String[0];

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Setter
    private ReplayStatus status;

    @Column(nullable = false)
    private int version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Replay(Workspace workspace, Project project, String title, ReplayType type, User createdBy) {
        this.workspace = workspace;
        this.project = project;
        this.title = title;
        this.type = type;
        this.createdBy = createdBy;
        this.status = ReplayStatus.DRAFT;
        this.version = 1;
    }

    public void incrementVersion() {
        version++;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = ReplayStatus.DRAFT;
        if (tags == null) tags = new String[0];
        if (stack == null) stack = new String[0];
        if (version < 1) version = 1;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
