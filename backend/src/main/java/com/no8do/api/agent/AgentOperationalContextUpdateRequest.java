package com.no8do.api.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record AgentOperationalContextUpdateRequest(
        @Valid RepositorySignal repository,
        @Size(max = 255) String branch,
        @Size(max = 1024) String workingDirectory,
        @Size(max = 20) List<@Valid ReferenceSignal> references,
        UUID workspaceHint) {

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
