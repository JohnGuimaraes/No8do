package com.no8do.api.integration;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/** Authenticated Integration identity; credentials are deliberately never retained. */
public final class IntegrationAuthenticationToken extends AbstractAuthenticationToken {
    private final IntegrationPrincipal principal;

    private IntegrationAuthenticationToken(IntegrationPrincipal principal) {
        super(List.of());
        this.principal = java.util.Objects.requireNonNull(principal, "principal");
        super.setAuthenticated(true);
    }

    static IntegrationAuthenticationToken verified(IntegrationPrincipal principal) {
        return new IntegrationAuthenticationToken(principal);
    }

    @Override public Object getCredentials() { return null; }
    @Override public Object getPrincipal() { return principal; }

    @Override
    public void setAuthenticated(boolean authenticated) {
        if (authenticated) throw new IllegalArgumentException("Use verified Integration authentication only.");
        super.setAuthenticated(false);
    }
}
