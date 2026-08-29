package com.no8do.api.workitem;

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
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "project_work_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectWorkItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @Setter
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Setter
    private ProjectWorkItemType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter
    private ProjectWorkItemStatus status;

    @Column(nullable = false, length = 180)
    @Setter
    private String title;

    @Column(columnDefinition = "text")
    @Setter
    private String details;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assignee_user_id") @Setter private User assignee;
    @Column(name = "due_date") @Setter private LocalDate dueDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @Setter
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    @Setter
    private Instant completedAt;

    public ProjectWorkItem(Project project, ProjectWorkItemType type, String title, String details, User createdBy) {
        this.project = project;
        this.type = type;
        this.status = ProjectWorkItemStatus.OPEN;
        this.title = title;
        this.details = details;
        this.createdBy = createdBy;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = ProjectWorkItemStatus.OPEN;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
