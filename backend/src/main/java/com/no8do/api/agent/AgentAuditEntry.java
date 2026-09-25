package com.no8do.api.agent;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agent_audit_entries")
public class AgentAuditEntry {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40, updatable = false)
    private AgentAuditEventType eventType;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "workspace_id", updatable = false)
    private UUID workspaceId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private JsonNode metadata;

    @Column(name = "recorded_at", nullable = false, insertable = false, updatable = false)
    private Instant recordedAt;

    protected AgentAuditEntry() {}

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public AgentAuditEventType getEventType() { return eventType; }
    public UUID getSessionId() { return sessionId; }
    public UUID getUserId() { return userId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public Instant getOccurredAt() { return occurredAt; }
    public JsonNode getMetadata() { return metadata == null ? null : metadata.deepCopy(); }
    public Instant getRecordedAt() { return recordedAt; }
}
