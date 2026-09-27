package com.no8do.api.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentCredentialRepository extends JpaRepository<AgentCredential, UUID> {

    Optional<AgentCredential> findByIdAndAgentId(UUID id, UUID agentId);

    Optional<AgentCredential> findByPublicCredentialId(String publicCredentialId);

    List<AgentCredential> findByAgentIdOrderByCreatedAtDescIdAsc(UUID agentId);

    List<AgentCredential> findByAgentIdAndStatusOrderByCreatedAtDescIdAsc(
            UUID agentId, AgentCredentialStatus status);

    long countByAgentIdAndStatus(UUID agentId, AgentCredentialStatus status);
}
