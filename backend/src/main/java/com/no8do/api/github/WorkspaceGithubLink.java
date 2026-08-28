package com.no8do.api.github;

import com.no8do.api.user.User;
import com.no8do.api.workspace.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "workspace_github_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceGithubLink {

    @Id
    @Column(name = "workspace_id")
    private UUID workspaceId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "github_connection_user_id", nullable = false)
    private UserGithubConnection githubConnection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_by")
    private User linkedBy;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    public WorkspaceGithubLink(Workspace workspace, UserGithubConnection githubConnection, User linkedBy) {
        this.workspace = workspace;
        this.githubConnection = githubConnection;
        this.linkedBy = linkedBy;
    }

    @PrePersist
    void prePersist() {
        linkedAt = Instant.now();
    }
}
