package com.no8do.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class IntegrationCredentialAuthenticationFilterTests {
    private final IntegrationAuthorizationVerificationService verification =
            org.mockito.Mockito.mock(IntegrationAuthorizationVerificationService.class);
    private final IntegrationAuthorizationUsageTouchService touch =
            org.mockito.Mockito.mock(IntegrationAuthorizationUsageTouchService.class);
    private final IntegrationCredentialAuthenticationFilter filter = new IntegrationCredentialAuthenticationFilter(
            verification, touch, Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC));

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void validCredentialCreatesDedicatedCredentialFreePrincipalOnlyOnAllowlistedRoute() throws Exception {
        UUID authorizationId = UUID.randomUUID();
        UUID grantorId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(verification.verify("no8do_int_selector.secret"))
                .thenReturn(Optional.of(new VerifiedIntegrationAuthorization(
                        authorizationId, grantorId, agentId, workspaceId)));
        MockHttpServletRequest request = request("GET", "/api/agent-protocol");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer no8do_int_selector.secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isInstanceOf(IntegrationAuthenticationToken.class);
            assertThat(authentication.isAuthenticated()).isTrue();
            assertThat(authentication.getCredentials()).isNull();
            assertThat(authentication.getAuthorities()).isEmpty();
            assertThat(authentication.getPrincipal()).isEqualTo(new IntegrationPrincipal(
                    authorizationId, agentId, workspaceId, grantorId));
            assertThat(authentication.getPrincipal()).isNotInstanceOf(com.no8do.api.auth.No8doUserDetails.class);
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(IntegrationCredentialAuthenticationFilter.CSRF_BYPASS_ATTRIBUTE)).isEqualTo(true);
        verify(touch).touch(authorizationId, Instant.parse("2026-09-29T12:00:00Z"));
    }

    @Test
    void recognizedInvalidCredentialFailsClosedAndClearsExistingHumanAuthentication() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("human", null, java.util.List.of()));
        when(verification.verify("no8do_int_malformed")).thenReturn(Optional.empty());
        MockHttpServletRequest request = request("GET", "/api/agent-protocol");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer no8do_int_malformed");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = org.mockito.Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(IntegrationCredentialAuthenticationFilter.CSRF_BYPASS_ATTRIBUTE)).isNull();
        verify(chain, never()).doFilter(request, response);
        verify(touch, never()).touch(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void validCredentialCannotAccessReplayOrOtherNonAllowlistedRoutes() throws Exception {
        when(verification.verify("no8do_int_selector.secret")).thenReturn(Optional.of(
                new VerifiedIntegrationAuthorization(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())));
        MockHttpServletRequest request = request("GET", "/api/replays");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer no8do_int_selector.secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = org.mockito.Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(request.getAttribute(IntegrationCredentialAuthenticationFilter.CSRF_BYPASS_ATTRIBUTE)).isNull();
        verify(chain, never()).doFilter(request, response);
        verify(touch, never()).touch(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void usageTouchFailureIsBestEffortAndDoesNotRejectVerifiedRequest() throws Exception {
        UUID authorizationId = UUID.randomUUID();
        when(verification.verify("no8do_int_selector.secret")).thenReturn(Optional.of(
                new VerifiedIntegrationAuthorization(authorizationId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())));
        org.mockito.Mockito.doThrow(new IllegalStateException("safe synthetic failure"))
                .when(touch).touch(org.mockito.ArgumentMatchers.eq(authorizationId), org.mockito.ArgumentMatchers.any());
        MockHttpServletRequest request = request("GET", "/api/agent-protocol");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer no8do_int_selector.secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = org.mockito.Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }
}
