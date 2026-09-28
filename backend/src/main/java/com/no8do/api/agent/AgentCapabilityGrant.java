package com.no8do.api.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "agent_capability_grants", uniqueConstraints = @UniqueConstraint(
        name = "uq_agent_capability_grants_agent_capability", columnNames = {"agent_id", "capability"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentCapabilityGrant {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false, updatable = false)
    private Agent agent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80, updatable = false)
    private AgentCapability capability;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "granted_by_user_id", updatable = false)
    private UUID grantedByUserId;

    AgentCapabilityGrant(UUID id, Agent agent, AgentCapability capability, Instant grantedAt, UUID grantedByUserId) {
        this.id = id;
        this.agent = agent;
        this.capability = capability;
        this.grantedAt = grantedAt;
        this.grantedByUserId = grantedByUserId;
    }
}
