package com.no8do.api.agent;

import java.time.Instant;
import java.util.UUID;

public record AgentSessionRevocationResult(Instant revokedAt, UUID revokedByUserId, boolean newlyRevoked) {}
