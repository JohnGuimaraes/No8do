package com.no8do.api.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GoogleOAuthConfiguration {

    private final String clientId;
    private final String clientSecret;

    public GoogleOAuthConfiguration(
            @Value("${no8do.google.client-id:}") String clientId,
            @Value("${no8do.google.client-secret:}") String clientSecret
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }
}
