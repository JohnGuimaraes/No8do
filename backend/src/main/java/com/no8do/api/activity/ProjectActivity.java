package com.no8do.api.activity;

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
import lombok.Setter;

@Entity
@Table(name = "project_activities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectActivity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @Setter
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @Setter
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter
    private ProjectActivityType type;

    @Column(nullable = false, columnDefinition = "text")
    @Setter
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ProjectActivity(Project project, User createdBy, ProjectActivityType type, String content) {
        this.project = project;
        this.createdBy = createdBy;
        this.type = type;
        this.content = content;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (type == null) {
            type = ProjectActivityType.UPDATE;
        }
        createdAt = Instant.now();
    }
}
