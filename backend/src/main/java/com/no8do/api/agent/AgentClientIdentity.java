package com.no8do.api.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentClientIdentity(
        @NotBlank @Size(max = 255) String clientName,
        @NotBlank @Size(max = 255) String clientVersion
) {}
