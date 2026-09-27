package com.no8do.api.agent;

import java.time.Instant;
import java.util.UUID;

/** The serialized credential is returned only by create and rotate responses. */
public record AgentCredentialIssueResponse(
        UUID id,
        String publicCredentialId,
        AgentCredentialStatus status,
        Instant createdAt,
        String credential
) {
    @Override
    public String toString() {
        return "AgentCredentialIssueResponse[id=" + id + ", publicCredentialId=" + publicCredentialId
                + ", status=" + status + ", createdAt=" + createdAt + ", credential=REDACTED]";
    }
}
