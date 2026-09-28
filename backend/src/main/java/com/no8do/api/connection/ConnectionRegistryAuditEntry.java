package com.no8do.api.connection;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "connection_registry_audit_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConnectionRegistryAuditEntry {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40, updatable = false)
    private ConnectionRegistryAuditEventType eventType;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "connection_id", nullable = false, updatable = false)
    private UUID connectionId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private JsonNode metadata;

    @Column(name = "recorded_at", nullable = false, insertable = false, updatable = false)
    private Instant recordedAt;

    ConnectionRegistryAuditEntry(UUID id, UUID eventId, ConnectionRegistryAuditEventType eventType,
            UUID actorUserId, UUID workspaceId, UUID connectionId, Instant occurredAt, JsonNode metadata) {
        this.id = id;
        this.eventId = eventId;
        this.eventType = eventType;
        this.actorUserId = actorUserId;
        this.workspaceId = workspaceId;
        this.connectionId = connectionId;
        this.occurredAt = occurredAt;
        this.metadata = metadata == null ? null : metadata.deepCopy();
    }

    @Override
    public String toString() {
        return "ConnectionRegistryAuditEntry[id=" + id + ", eventId=" + eventId + ", eventType=" + eventType
                + ", actorUserId=" + actorUserId + ", workspaceId=" + workspaceId
                + ", connectionId=" + connectionId + ", occurredAt=" + occurredAt + "]";
    }
}
