package com.no8do.api.agent;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentAuditEntryRepository extends JpaRepository<AgentAuditEntry, UUID>,
        JpaSpecificationExecutor<AgentAuditEntry> {
    @Modifying
    @Query(value = """
        insert into agent_audit_entries
            (id, event_id, event_type, session_id, user_id, workspace_id, occurred_at, metadata)
        values
            (:id, :eventId, :eventType, :sessionId, :userId, :workspaceId, :occurredAt, cast(:metadata as jsonb))
        on conflict (event_id) do nothing
        """, nativeQuery = true)
    int insertIfEventAbsent(@Param("id") UUID id, @Param("eventId") UUID eventId,
            @Param("eventType") String eventType, @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId, @Param("workspaceId") UUID workspaceId,
            @Param("occurredAt") Instant occurredAt, @Param("metadata") String metadata);

}
