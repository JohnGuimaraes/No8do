package com.no8do.api.integration;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Strict input DTO: unknown members are rejected even when the global mapper ignores them. */
public final class IntegrationBootstrapStartRequest {
    @JsonProperty("installationId") @NotNull private UUID installationId;
    @JsonProperty("integrationVersion") @NotBlank @Size(max = 64) private String integrationVersion;
    @JsonProperty("hostType") @NotNull private IntegrationHostType hostType;
    @JsonProperty("displayLabel") @Size(max = 120) private String displayLabel;
    @JsonProperty("codeChallenge") @NotBlank @Size(max = 43) private String codeChallenge;
    @JsonProperty("codeChallengeMethod") @NotBlank @Size(max = 8) private String codeChallengeMethod;

    public IntegrationBootstrapStartRequest() {}

    @JsonAnySetter
    public void rejectUnknown(String name, JsonNode ignored) {
        throw new IllegalArgumentException("Unknown bootstrap request field");
    }

    public UUID installationId() { return installationId; }
    public String integrationVersion() { return integrationVersion; }
    public IntegrationHostType hostType() { return hostType; }
    public String displayLabel() { return displayLabel; }
    public String codeChallenge() { return codeChallenge; }
    public String codeChallengeMethod() { return codeChallengeMethod; }
}
