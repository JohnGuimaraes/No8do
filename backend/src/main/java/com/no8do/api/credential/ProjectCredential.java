package com.no8do.api.credential;

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
@Table(name = "project_credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectCredential {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @Setter
    private Project project;

    @Column(nullable = false, length = 180)
    @Setter
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Setter
    private ProjectCredentialType type;

    @Column(length = 255)
    @Setter
    private String username;

    @Column(name = "secret_ciphertext", nullable = false, columnDefinition = "text")
    @Setter
    private String secretCiphertext;

    @Column(name = "secret_iv", nullable = false, length = 64)
    @Setter
    private String secretIv;

    @Column(name = "key_version", nullable = false)
    @Setter
    private Integer keyVersion;

    @Column(length = 500)
    @Setter
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    @Setter
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProjectCredential(
            Project project,
            String label,
            ProjectCredentialType type,
            String username,
            String secretCiphertext,
            String secretIv,
            Integer keyVersion,
            String notes,
            User createdBy
    ) {
        this.project = project;
        this.label = label;
        this.type = type;
        this.username = username;
        this.secretCiphertext = secretCiphertext;
        this.secretIv = secretIv;
        this.keyVersion = keyVersion;
        this.notes = notes;
        this.createdBy = createdBy;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
