package com.no8do.api.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentCredentialRepository extends JpaRepository<AgentCredential, UUID> {

    Optional<AgentCredential> findByIdAndAgentId(UUID id, UUID agentId);

    Optional<AgentCredential> findByPublicCredentialId(String publicCredentialId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select credential from AgentCredential credential where credential.publicCredentialId = :publicCredentialId")
    Optional<AgentCredential> findByPublicCredentialIdForUpdate(
            @Param("publicCredentialId") String publicCredentialId);

    List<AgentCredential> findByAgentIdOrderByCreatedAtDescIdAsc(UUID agentId);

    List<AgentCredential> findByAgentIdAndStatusOrderByCreatedAtDescIdAsc(
            UUID agentId, AgentCredentialStatus status);

    long countByAgentIdAndStatus(UUID agentId, AgentCredentialStatus status);
}
