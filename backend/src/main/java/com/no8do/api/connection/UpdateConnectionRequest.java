package com.no8do.api.connection;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Size;

public record UpdateConnectionRequest(
        @Size(max = 160) String name,
        JsonNode metadata
) {}
