package com.no8do.api.integration;

import java.time.Instant;
import java.util.UUID;

public final class IntegrationBootstrapStartResponse {
    private final UUID requestId;
    private final String deviceCode;
    private final String userCode;
    private final String verificationUri;
    private final long expiresIn;
    private final int interval;

    IntegrationBootstrapStartResponse(UUID requestId, String deviceCode, String userCode,
            String verificationUri, long expiresIn, int interval) {
        this.requestId = requestId;
        this.deviceCode = deviceCode;
        this.userCode = userCode;
        this.verificationUri = verificationUri;
        this.expiresIn = expiresIn;
        this.interval = interval;
    }

    public UUID getRequestId() { return requestId; }
    public String getDeviceCode() { return deviceCode; }
    public String getUserCode() { return userCode; }
    public String getVerificationUri() { return verificationUri; }
    public long getExpiresIn() { return expiresIn; }
    public int getInterval() { return interval; }

    @Override public String toString() {
        return "IntegrationBootstrapStartResponse[requestId=" + requestId + ", credentials=REDACTED]";
    }
}
