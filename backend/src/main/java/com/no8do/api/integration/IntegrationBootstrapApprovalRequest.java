package com.no8do.api.integration;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class IntegrationBootstrapApprovalRequest {
    @JsonProperty("userCode") @NotBlank @Size(max = 16) private String userCode;
    @JsonProperty("workspaceId") @NotNull private UUID workspaceId;
    @JsonProperty("existingAgentId") private UUID existingAgentId;
    @JsonProperty("newAgent") @Valid private NewAgent newAgent;
    public IntegrationBootstrapApprovalRequest() {}
    @JsonAnySetter public void rejectUnknown(String name, JsonNode ignored) {
        throw new IllegalArgumentException("Unknown bootstrap request field");
    }
    public String userCode() { return userCode; }
    public UUID workspaceId() { return workspaceId; }
    public UUID existingAgentId() { return existingAgentId; }
    public NewAgent newAgent() { return newAgent; }

    public static final class NewAgent {
        @JsonProperty("name") @NotBlank @Size(max = 160) private String name;
        public NewAgent() {}
        @JsonAnySetter public void rejectUnknown(String field, JsonNode ignored) {
            throw new IllegalArgumentException("Unknown Agent request field");
        }
        public String name() { return name; }
        @Override public String toString() { return "NewAgent[name=REDACTED]"; }
    }

    @Override public String toString() { return "IntegrationBootstrapApprovalRequest[code=REDACTED]"; }
}
