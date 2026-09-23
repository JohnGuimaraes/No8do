package com.no8do.api.agent;

import jakarta.validation.constraints.NotNull;

public record AgentRuntimeModeRequest(@NotNull AgentRuntimeMode runtimeMode) {
}
