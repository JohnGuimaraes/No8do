package com.no8do.api.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateAgentRequest(
        @NotBlank @Size(max = 160) String name,
        String description,
        String providerDescriptor
) {
}
