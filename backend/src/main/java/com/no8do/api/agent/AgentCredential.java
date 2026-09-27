package com.no8do.api.agent;

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
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "agent_credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentCredential {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false, updatable = false)
    private Agent agent;

    @Column(name = "public_credential_id", nullable = false, unique = true, length = 64, updatable = false)
    private String publicCredentialId;

    @Column(name = "secret_hash", nullable = false, length = 64, updatable = false)
    private String secretHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentCredentialStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    AgentCredential(UUID id, Agent agent, String publicCredentialId, String secretHash, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.agent = Objects.requireNonNull(agent, "agent");
        this.publicCredentialId = Objects.requireNonNull(publicCredentialId, "publicCredentialId");
        this.secretHash = Objects.requireNonNull(secretHash, "secretHash");
        this.status = AgentCredentialStatus.ACTIVE;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    boolean revoke(Instant at) {
        Objects.requireNonNull(at, "at");
        if (status == AgentCredentialStatus.REVOKED) return false;
        status = AgentCredentialStatus.REVOKED;
        revokedAt = at;
        return true;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = AgentCredentialStatus.ACTIVE;
        if (createdAt == null) createdAt = Instant.now();
    }

    @Override
    public String toString() {
        return "AgentCredential[id=" + id + ", agentId=" + (agent == null ? null : agent.getId())
                + ", publicCredentialId=" + publicCredentialId + ", status=" + status + ", createdAt=" + createdAt
                + ", revokedAt=" + revokedAt + "]";
    }
}
