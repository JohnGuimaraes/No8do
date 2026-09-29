package com.no8do.api.agent;

import java.util.List;
import java.util.UUID;

public interface OperationalProjectRepositoryAssociationLookup {
    List<UUID> findProjectIds(UUID workspaceId, String providerRepositoryId);
}
