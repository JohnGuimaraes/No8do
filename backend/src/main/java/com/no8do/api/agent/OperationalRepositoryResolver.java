package com.no8do.api.agent;

import java.util.UUID;

/** Resolves a canonical repository signal using the effective authorized workspace. */
public interface OperationalRepositoryResolver {
    RepositoryIdentityResolution resolve(OperationalRepositoryLocator repository, UUID workspaceId);

    record RepositoryIdentityResolution(boolean supported, boolean found, String providerRepositoryId) {
        public RepositoryIdentityResolution {
            if (found && (!supported || providerRepositoryId == null || providerRepositoryId.isBlank())) {
                throw new IllegalArgumentException("Supported repository resolution requires an identity");
            }
            if (!found && providerRepositoryId != null) {
                throw new IllegalArgumentException("Unsupported repository resolution cannot include an identity");
            }
        }

        public static RepositoryIdentityResolution unsupported() {
            return new RepositoryIdentityResolution(false, false, null);
        }

        public static RepositoryIdentityResolution inaccessible() {
            return new RepositoryIdentityResolution(true, false, null);
        }
    }
}
