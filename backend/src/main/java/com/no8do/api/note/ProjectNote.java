package com.no8do.api.note;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "project_notes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectNote {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @Setter
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    @Setter
    private User createdBy;

    @Column(nullable = false, columnDefinition = "text")
    @Setter
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter
    private ProjectNoteType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectNote(Project project, User createdBy, String content, ProjectNoteType type) {
        this.project = project;
        this.createdBy = createdBy;
        this.content = content;
        this.type = type == null ? ProjectNoteType.NOTE : type;
    }

    public ProjectNote(Project project, User createdBy, String content) { this(project, createdBy, content, ProjectNoteType.NOTE); }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
