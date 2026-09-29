package com.no8do.api.integration;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class IntegrationBootstrapExchangeRequest {
    @JsonProperty("deviceCode") @NotBlank @Size(max = 64) private String deviceCode;
    @JsonProperty("codeVerifier") @NotBlank @Size(max = 128) private String codeVerifier;
    public IntegrationBootstrapExchangeRequest() {}
    @JsonAnySetter public void rejectUnknown(String name, JsonNode ignored) {
        throw new IllegalArgumentException("Unknown bootstrap request field");
    }
    public String deviceCode() { return deviceCode; }
    public String codeVerifier() { return codeVerifier; }
    @Override public String toString() { return "IntegrationBootstrapExchangeRequest[credentials=REDACTED]"; }
}
