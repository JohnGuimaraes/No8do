package com.no8do.api.connection;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateConnectionRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String provider,
        @NotBlank @Size(max = 160) String name,
        @NotNull ConnectionCredentialReferenceType credentialReferenceType,
        UUID credentialReferenceId,
        JsonNode metadata
) {}
