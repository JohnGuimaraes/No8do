package com.no8do.api.agent;

import java.util.UUID;

/** Internal identity returned after credential verification; it contains no credential material. */
public record VerifiedAgentCredential(UUID credentialId, UUID agentId, UUID workspaceId) {}
