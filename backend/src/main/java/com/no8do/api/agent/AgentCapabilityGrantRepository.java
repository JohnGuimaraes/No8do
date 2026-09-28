package com.no8do.api.agent;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentCapabilityGrantRepository extends JpaRepository<AgentCapabilityGrant, UUID> {

    List<AgentCapabilityGrant> findByAgent_IdOrderByGrantedAtAscCapabilityAsc(UUID agentId);

    @Query("select g.capability from AgentCapabilityGrant g where g.agent.id = :agentId")
    Set<AgentCapability> findCapabilitiesByAgentId(@Param("agentId") UUID agentId);

    @Modifying
    @Query(value = """
        insert into agent_capability_grants (id, agent_id, capability, granted_at, granted_by_user_id)
        values (:id, :agentId, :capability, :grantedAt, :grantedByUserId)
        on conflict (agent_id, capability) do nothing
        """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("agentId") UUID agentId,
            @Param("capability") String capability, @Param("grantedAt") Instant grantedAt,
            @Param("grantedByUserId") UUID grantedByUserId);

    @Modifying
    @Query(value = "delete from agent_capability_grants where agent_id = :agentId and capability = :capability",
            nativeQuery = true)
    int deleteGrant(@Param("agentId") UUID agentId, @Param("capability") String capability);

    long countByAgent_Id(UUID agentId);
}
