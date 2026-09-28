package com.no8do.api.connection;

import com.fasterxml.jackson.databind.JsonNode;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "connections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Connection {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, updatable = false)
    private Workspace workspace;

    @Column(nullable = false, length = 64, updatable = false)
    private String provider;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ConnectionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_reference_type", nullable = false, length = 32, updatable = false)
    private ConnectionCredentialReferenceType credentialReferenceType;

    /** Opaque adapter-owned identifier; it is never returned by public DTOs or written to audit metadata. */
    @Column(name = "credential_reference_id", updatable = false)
    private UUID credentialReferenceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode metadata;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", updatable = false)
    private User createdByUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    Connection(UUID id, Workspace workspace, String provider, String name,
            ConnectionCredentialReferenceType credentialReferenceType, UUID credentialReferenceId,
            JsonNode metadata, User createdByUser, Instant createdAt) {
        this.id = id;
        this.workspace = workspace;
        this.provider = provider;
        this.name = name;
        this.status = ConnectionStatus.CONFIGURED;
        this.credentialReferenceType = credentialReferenceType;
        this.credentialReferenceId = credentialReferenceId;
        this.metadata = metadata == null ? null : metadata.deepCopy();
        this.createdByUser = createdByUser;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    boolean update(String name, JsonNode metadata, Instant at) {
        boolean changed = false;
        if (name != null && !this.name.equals(name)) {
            this.name = name;
            changed = true;
        }
        if (metadata != null && !this.metadata.equals(metadata)) {
            this.metadata = metadata.deepCopy();
            changed = true;
        }
        if (changed) updatedAt = at;
        return changed;
    }

    boolean disconnect(Instant at) {
        if (status == ConnectionStatus.DISCONNECTED) return false;
        status = ConnectionStatus.DISCONNECTED;
        disconnectedAt = at;
        updatedAt = at;
        return true;
    }

    @Override
    public String toString() {
        return "Connection[id=" + id + ", workspaceId=" + (workspace == null ? null : workspace.getId())
                + ", provider=" + provider + ", status=" + status + "]";
    }
}
