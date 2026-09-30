package com.no8do.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntegrationAuthenticationTokenTests {
    @Test
    void verifiedAuthenticationContainsOnlyTheFourFieldPrincipalAndNoAuthoritiesOrCredentials() {
        IntegrationPrincipal principal = new IntegrationPrincipal(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());

        IntegrationAuthenticationToken authentication = IntegrationAuthenticationToken.verified(principal);

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(principal);
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities()).isEmpty();
        assertThatThrownBy(() -> authentication.setAuthenticated(true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(principal.toString()).doesNotContain("ROLE_");
    }
}
