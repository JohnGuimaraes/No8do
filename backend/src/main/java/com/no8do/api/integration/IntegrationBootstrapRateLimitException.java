package com.no8do.api.integration;

public final class IntegrationBootstrapRateLimitException extends RuntimeException {
    private final int retryAfterSeconds;
    IntegrationBootstrapRateLimitException(int retryAfterSeconds) {
        super("Integration bootstrap rate limit exceeded");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }
    public int retryAfterSeconds() { return retryAfterSeconds; }
}
