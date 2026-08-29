package com.no8do.api.workspace;

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
@Table(name = "workspace_invites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceInvite {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceInviteRole role;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public WorkspaceInvite(Workspace workspace, String email, WorkspaceInviteRole role, String tokenHash, User createdBy, Instant expiresAt) {
        this.workspace = workspace;
        this.email = email;
        this.role = role;
        this.tokenHash = tokenHash;
        this.createdBy = createdBy;
        this.expiresAt = expiresAt;
    }

    public void accept(Instant now) { acceptedAt = now; }
    public void revoke(Instant now) { revokedAt = now; }
    public boolean isPendingAt(Instant now) { return acceptedAt == null && revokedAt == null && expiresAt.isAfter(now); }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }
}
