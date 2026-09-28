package com.no8do.api.agent;

import com.no8do.api.connection.Connection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "agent_connection_assignments", uniqueConstraints = @UniqueConstraint(
        name = "uq_agent_connection_assignments_agent_connection", columnNames = {"agent_id", "connection_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentConnectionAssignment {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false, updatable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false, updatable = false)
    private Connection connection;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by_user_id", updatable = false)
    private UUID assignedByUserId;

    AgentConnectionAssignment(UUID id, Agent agent, Connection connection, Instant assignedAt,
            UUID assignedByUserId) {
        this.id = id;
        this.agent = agent;
        this.connection = connection;
        this.assignedAt = assignedAt;
        this.assignedByUserId = assignedByUserId;
    }
}
