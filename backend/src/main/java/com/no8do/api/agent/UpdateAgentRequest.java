package com.no8do.api.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PATCH replaces editable details: name is required; omitted or null optional fields are cleared. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateAgentRequest(
        @NotBlank @Size(max = 160) String name,
        String description,
        String providerDescriptor
) {
}
