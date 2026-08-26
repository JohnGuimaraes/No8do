package com.no8do.api.github;

import com.no8do.api.user.User;
import com.no8do.api.workspace.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "workspace_github_app_install_states")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceGithubAppInstallState {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 128)
    private String state;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public WorkspaceGithubAppInstallState(String state, Workspace workspace, User user, Instant expiresAt) {
        this.state = state;
        this.workspace = workspace;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }
}
