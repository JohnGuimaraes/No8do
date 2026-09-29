package com.no8do.api.agent;

public record OperationalContextIntegrationContract(int version, String getMethod, String updateMethod,
        String optimisticConcurrency) {
    public OperationalContextIntegrationContract {
        if (version <= 0 || getMethod == null || updateMethod == null
                || !"EXPECTED_VERSION".equals(optimisticConcurrency)) {
            throw new IllegalArgumentException("Operational Context integration contract is invalid.");
        }
    }
}
