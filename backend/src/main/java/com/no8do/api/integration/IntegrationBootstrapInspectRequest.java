package com.no8do.api.integration;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class IntegrationBootstrapInspectRequest {
    @JsonProperty("userCode") @NotBlank @Size(max = 16) private String userCode;
    public IntegrationBootstrapInspectRequest() {}
    @JsonAnySetter public void rejectUnknown(String name, JsonNode ignored) {
        throw new IllegalArgumentException("Unknown bootstrap request field");
    }
    public String userCode() { return userCode; }
    @Override public String toString() { return "IntegrationBootstrapInspectRequest[code=REDACTED]"; }
}
