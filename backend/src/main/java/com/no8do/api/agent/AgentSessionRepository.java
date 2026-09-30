package com.no8do.api.agent;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentSessionRepository extends JpaRepository<AgentSession, UUID> {
    @Query("select session from AgentSession session where session.agent.id = :agentId")
    Page<AgentSession> findAgentSessions(@Param("agentId") UUID agentId, Pageable pageable);

    @Query("""
        select new com.no8do.api.agent.AgentSessionAggregate(
            count(session.id),
            coalesce(sum(case when session.revokedAt is null and session.disconnectedAt is null
                and coalesce(session.lastSeenAt, session.registeredAt) >= :disconnectCutoff then 1 else 0 end), 0),
            coalesce(sum(case when session.revokedAt is null and session.disconnectedAt is null
                and coalesce(session.lastSeenAt, session.registeredAt) >= :disconnectCutoff
                and session.lastActivityAt is not null and session.lastActivityAt >= :activeCutoff then 1 else 0 end), 0),
            coalesce(sum(case when session.revokedAt is null and session.disconnectedAt is null
                and coalesce(session.lastSeenAt, session.registeredAt) >= :disconnectCutoff
                and session.lastActivityAt is null then 1 else 0 end), 0),
            max(session.lastSeenAt), max(session.lastActivityAt), max(session.registeredAt)
        )
        from AgentSession session
        where session.agent.id = :agentId
        """)
    AgentSessionAggregate aggregateForAgent(@Param("agentId") UUID agentId,
            @Param("activeCutoff") Instant activeCutoff, @Param("disconnectCutoff") Instant disconnectCutoff);

    @Query("""
        select session from AgentSession session
        where session.userId = :userId
          and (:workspaceId is null or session.workspaceId = :workspaceId)
          and (:runtimeMode is null or session.runtimeMode = :runtimeMode)
          and (:clientName = '' or lower(session.clientName) like lower(concat('%', :clientName, '%')))
        """)
    Page<AgentSession> findOwnedForDiscovery(@Param("userId") UUID userId,
            @Param("workspaceId") UUID workspaceId, @Param("runtimeMode") AgentRuntimeMode runtimeMode,
            @Param("clientName") String clientName, Pageable pageable);

    @Query("""
        select session from AgentSession session
        where session.workspaceId = :workspaceId
          and (:runtimeMode is null or session.runtimeMode = :runtimeMode)
          and (:clientName = '' or lower(session.clientName) like lower(concat('%', :clientName, '%')))
        """)
    Page<AgentSession> findWorkspaceSessionsForAdmin(@Param("workspaceId") UUID workspaceId,
            @Param("runtimeMode") AgentRuntimeMode runtimeMode, @Param("clientName") String clientName,
            Pageable pageable);

    Optional<AgentSession> findByIdAndUserId(UUID id, UUID userId);

    Optional<AgentSession> findByTransportAndTransportSessionFingerprintAndRevokedAtIsNull(
            AgentTransport transport, String fingerprint);
    Optional<AgentSession> findByTransportAndTransportSessionFingerprint(AgentTransport transport, String fingerprint);
    long countByTransportAndTransportSessionFingerprint(AgentTransport transport, String fingerprint);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AgentSession session where session.id = :sessionId")
    Optional<AgentSession> findByIdForUpdate(@Param("sessionId") UUID sessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update agent_sessions set last_seen_at = :lastSeenAt where id = :sessionId and user_id = :userId and disconnected_at is null and revoked_at is null", nativeQuery = true)
    int updateLastSeenAt(@Param("sessionId") UUID sessionId, @Param("userId") UUID userId,
            @Param("lastSeenAt") Instant lastSeenAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update agent_sessions set last_seen_at = :lastSeenAt, last_activity_at = :lastSeenAt where id = :sessionId and user_id = :userId and disconnected_at is null and revoked_at is null", nativeQuery = true)
    int updateActivityTimestamps(@Param("sessionId") UUID sessionId, @Param("userId") UUID userId,
            @Param("lastSeenAt") Instant lastSeenAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update agent_sessions set disconnected_at = :disconnectedAt where id = :sessionId and user_id = :userId and disconnected_at is null", nativeQuery = true)
    int updateDisconnectedAtIfAbsent(@Param("sessionId") UUID sessionId, @Param("userId") UUID userId,
            @Param("disconnectedAt") Instant disconnectedAt);

    @Modifying
    @Query(value = """
        insert into agent_sessions (id, user_id, workspace_id, client_name, client_version, transport,
            protocol_name, protocol_version, registered_at, last_seen_at, last_activity_at, transport_session_fingerprint,
            agent_id, agent_credential_id, integration_authorization_id)
        values (:id, :userId, :workspaceId, :clientName, :clientVersion, :transport,
            :protocolName, :protocolVersion, :registeredAt, :registeredAt, null, :fingerprint,
            :agentId, :agentCredentialId, :integrationAuthorizationId)
        on conflict (transport, transport_session_fingerprint) where revoked_at is null do nothing
        """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId,
            @Param("workspaceId") UUID workspaceId, @Param("clientName") String clientName,
            @Param("clientVersion") String clientVersion, @Param("transport") String transport,
            @Param("protocolName") String protocolName, @Param("protocolVersion") int protocolVersion,
            @Param("registeredAt") Instant registeredAt, @Param("fingerprint") String fingerprint,
            @Param("agentId") UUID agentId, @Param("agentCredentialId") UUID agentCredentialId,
            @Param("integrationAuthorizationId") UUID integrationAuthorizationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update agent_sessions set last_seen_at = :lastSeenAt where id = :sessionId "
            + "and integration_authorization_id = :authorizationId and disconnected_at is null and revoked_at is null",
            nativeQuery = true)
    int updateLastSeenAtForIntegration(@Param("sessionId") UUID sessionId,
            @Param("authorizationId") UUID authorizationId, @Param("lastSeenAt") Instant lastSeenAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update agent_sessions set last_seen_at = :lastSeenAt, last_activity_at = :lastSeenAt "
            + "where id = :sessionId and integration_authorization_id = :authorizationId "
            + "and disconnected_at is null and revoked_at is null", nativeQuery = true)
    int updateActivityTimestampsForIntegration(@Param("sessionId") UUID sessionId,
            @Param("authorizationId") UUID authorizationId, @Param("lastSeenAt") Instant lastSeenAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update agent_sessions set disconnected_at = :disconnectedAt where id = :sessionId "
            + "and integration_authorization_id = :authorizationId and disconnected_at is null and revoked_at is null",
            nativeQuery = true)
    int updateDisconnectedAtForIntegration(@Param("sessionId") UUID sessionId,
            @Param("authorizationId") UUID authorizationId, @Param("disconnectedAt") Instant disconnectedAt);
}
