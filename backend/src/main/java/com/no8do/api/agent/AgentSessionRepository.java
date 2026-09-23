package com.no8do.api.agent;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentSessionRepository extends JpaRepository<AgentSession, UUID> {
    Optional<AgentSession> findByTransportAndTransportSessionFingerprint(AgentTransport transport, String fingerprint);
    long countByTransportAndTransportSessionFingerprint(AgentTransport transport, String fingerprint);

    @Modifying
    @Query(value = """
        insert into agent_sessions (id, user_id, workspace_id, client_name, client_version, transport,
            protocol_name, protocol_version, registered_at, transport_session_fingerprint)
        values (:id, :userId, :workspaceId, :clientName, :clientVersion, :transport,
            :protocolName, :protocolVersion, :registeredAt, :fingerprint)
        on conflict (transport, transport_session_fingerprint) do nothing
        """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId,
            @Param("workspaceId") UUID workspaceId, @Param("clientName") String clientName,
            @Param("clientVersion") String clientVersion, @Param("transport") String transport,
            @Param("protocolName") String protocolName, @Param("protocolVersion") int protocolVersion,
            @Param("registeredAt") Instant registeredAt, @Param("fingerprint") String fingerprint);
}
