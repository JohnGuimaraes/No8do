package com.no8do.api.integration;

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

/** Append-only provenance IDs intentionally have no cascading foreign keys. */
@Entity
@Table(name = "integration_authorization_audit_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntegrationAuthorizationAuditEntry {
    @Id @Column(nullable = false, updatable = false) private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40, updatable = false)
    private IntegrationAuthorizationAuditEventType eventType;
    @Column(name = "actor_user_id", nullable = false, updatable = false) private UUID actorUserId;
    @Column(name = "workspace_id", updatable = false) private UUID workspaceId;
    @Column(name = "agent_id", updatable = false) private UUID agentId;
    @Column(name = "request_id", updatable = false) private UUID requestId;
    @Column(name = "authorization_id", updatable = false) private UUID authorizationId;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private JsonNode metadata;

    IntegrationAuthorizationAuditEntry(UUID id, IntegrationAuthorizationAuditEventType eventType,
            UUID actorUserId, UUID workspaceId, UUID agentId, UUID requestId, UUID authorizationId,
            Instant occurredAt, JsonNode metadata) {
        this.id = id; this.eventType = eventType; this.actorUserId = actorUserId; this.workspaceId = workspaceId;
        this.agentId = agentId; this.requestId = requestId; this.authorizationId = authorizationId;
        this.occurredAt = occurredAt; this.metadata = metadata == null ? null : metadata.deepCopy();
    }

    @Override public String toString() {
        return "IntegrationAuthorizationAuditEntry[id=" + id + ", eventType=" + eventType
                + ", actorUserId=" + actorUserId + ", requestId=" + requestId + "]";
    }
}
