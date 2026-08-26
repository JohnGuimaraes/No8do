package com.no8do.api.technicalinfo;

import com.no8do.api.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
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
@Table(name = "project_technical_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectTechnicalInfo {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "project_id", nullable = false)
    @Setter
    private Project project;

    @Column(columnDefinition = "text")
    @Setter
    private String stack;

    @Column(name = "production_url", length = 1000)
    @Setter
    private String productionUrl;

    @Column(name = "development_url", length = 1000)
    @Setter
    private String developmentUrl;

    @Column(name = "local_path", length = 2000)
    @Setter
    private String localPath;

    @Column(name = "run_command", length = 1000)
    @Setter
    private String runCommand;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectTechnicalInfo(Project project) {
        this.project = project;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
