package com.no8do.api.github;

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
@Table(name = "workspace_github_app_installations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceGithubAppInstallation {

    @Id
    @Column(name = "workspace_id")
    private UUID workspaceId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "installation_id", nullable = false)
    private long installationId;

    @Column(name = "account_id", nullable = false)
    private long accountId;

    @Column(name = "account_login", nullable = false, length = 255)
    private String accountLogin;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private GithubAppInstallationAccountType accountType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "configured_by", nullable = false)
    private User configuredBy;

    @Column(name = "configured_at", nullable = false, updatable = false)
    private Instant configuredAt;

    public WorkspaceGithubAppInstallation(Workspace workspace, User configuredBy, GithubAppInstallationMetadata metadata) {
        this.workspace = workspace;
        this.configuredBy = configuredBy;
        replaceMetadata(metadata);
    }

    public void replaceMetadata(GithubAppInstallationMetadata metadata) {
        installationId = metadata.installationId();
        accountId = metadata.accountId();
        accountLogin = metadata.accountLogin();
        accountType = metadata.accountType();
    }

    @PrePersist
    void prePersist() {
        configuredAt = Instant.now();
    }
}
