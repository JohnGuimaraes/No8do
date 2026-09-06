package com.no8do.api.replay;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateReplayRelationRequest(@NotNull UUID targetReplayId, @NotNull ReplayRelationType type) {}
