package com.no8do.api.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChangeAgentLifecycleRequest(@NotNull AgentLifecycleStatus status) {
}
