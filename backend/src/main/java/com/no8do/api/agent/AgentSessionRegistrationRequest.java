package com.no8do.api.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AgentSessionRegistrationRequest(
        @NotBlank @Size(max = 255) String clientName,
        @NotBlank @Size(max = 255) String clientVersion,
        UUID workspaceId,
        @NotNull AgentTransport transport,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String transportSessionFingerprint
) {
    public AgentClientIdentity clientIdentity() {
        return new AgentClientIdentity(clientName, clientVersion);
    }
}
