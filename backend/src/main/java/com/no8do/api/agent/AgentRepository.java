package com.no8do.api.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentRepository extends JpaRepository<Agent, UUID> {

    Optional<Agent> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select agent from Agent agent where agent.id = :agentId and agent.workspace.id = :workspaceId")
    Optional<Agent> findByIdAndWorkspaceIdForUpdate(@Param("agentId") UUID agentId,
            @Param("workspaceId") UUID workspaceId);

    List<Agent> findByWorkspaceIdOrderByUpdatedAtDescIdAsc(UUID workspaceId);
}
