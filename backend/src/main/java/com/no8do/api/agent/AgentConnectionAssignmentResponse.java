package com.no8do.api.agent;

import com.no8do.api.connection.ConnectionStatus;
import java.time.Instant;
import java.util.UUID;

/** Minimal safe projection; credential references and Connection metadata are intentionally omitted. */
public record AgentConnectionAssignmentResponse(UUID connectionId, String name, String provider,
        ConnectionStatus status, Instant assignedAt) {}
