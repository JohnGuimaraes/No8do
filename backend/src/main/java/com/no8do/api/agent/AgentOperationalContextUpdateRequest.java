package com.no8do.api.agent;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record AgentOperationalContextUpdateRequest(
        @JsonProperty(value = "expectedVersion", required = true) JsonNode expectedVersion,
        @Valid RepositorySignal repository,
        @Size(max = 255) String branch,
        @Size(max = 1024) String workingDirectory,
        @Size(max = 20) List<@Valid ReferenceSignal> references,
        UUID workspaceHint) {

    public AgentOperationalContextUpdateRequest {
        if (expectedVersion == null) throw new IllegalArgumentException("expectedVersion is required");
        if (!expectedVersion.isNull() && (!expectedVersion.isIntegralNumber()
                || !expectedVersion.canConvertToLong() || expectedVersion.longValue() < 0)) {
            throw new IllegalArgumentException("expectedVersion must be null or a non-negative integer");
        }
    }

    /** Keeps direct domain-test construction concise while explicitly expecting an absent snapshot. */
    public AgentOperationalContextUpdateRequest(RepositorySignal repository, String branch,
            String workingDirectory, List<ReferenceSignal> references, UUID workspaceHint) {
        this(NullNode.instance, repository, branch, workingDirectory, references, workspaceHint);
    }

    public Long expectedVersionValue() {
        return expectedVersion.isNull() ? null : expectedVersion.longValue();
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unknown operational context field");
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record RepositorySignal(String vcs, @Size(max = 64) String provider,
            @Size(max = 253) String host, @Size(max = 512) String namespace,
            @Size(max = 255) String name) {
        @JsonAnySetter
        public void rejectUnknownField(String field, Object value) {
            throw new IllegalArgumentException("Unknown operational context field");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ReferenceSignal(AgentContextReferenceKind kind, @Size(max = 64) String provider,
            @Size(max = 128) String key) {
        @JsonAnySetter
        public void rejectUnknownField(String field, Object value) {
            throw new IllegalArgumentException("Unknown operational context field");
        }
    }
}
