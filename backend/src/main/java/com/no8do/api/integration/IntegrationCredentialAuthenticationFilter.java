package com.no8do.api.integration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class IntegrationCredentialAuthenticationFilter extends OncePerRequestFilter {
    public static final String CSRF_BYPASS_ATTRIBUTE = IntegrationCredentialAuthenticationFilter.class.getName() + ".csrfBypass";
    private static final String PREFIX = "no8do_int_";
    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationCredentialAuthenticationFilter.class);
    private static final RequestMatcher ALLOWLIST = new OrRequestMatcher(List.of(
            new AntPathRequestMatcher("/api/agent-protocol", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/agent-sessions", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/api/agent-sessions/*/heartbeat", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/api/agent-sessions/*/disconnect", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/api/agent-sessions/*/context", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/agent-sessions/*/operational-context/state", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/agent-sessions/*/operational-context", HttpMethod.PUT.name()),
            new AntPathRequestMatcher("/api/integration-runtime/replays", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/integration-runtime/replays/search", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/integration-runtime/replays/similar", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/api/integration-runtime/replays/*", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/integration-runtime/replays/*/quality", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/integration-runtime/replays/*/versions", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/integration-runtime/replays/*/versions/*", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/integration-runtime/replays/*/relations", HttpMethod.GET.name())));

    private final IntegrationAuthorizationVerificationService verificationService;
    private final IntegrationAuthorizationUsageTouchService usageTouchService;
    private final Clock clock;

    public IntegrationCredentialAuthenticationFilter(
            IntegrationAuthorizationVerificationService verificationService,
            IntegrationAuthorizationUsageTouchService usageTouchService, Clock clock) {
        this.verificationService = verificationService;
        this.usageTouchService = usageTouchService;
        this.clock = clock;
    }

    public static RequestMatcher allowlistMatcher() { return ALLOWLIST; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")
                || !header.substring("Bearer ".length()).startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        SecurityContextHolder.clearContext();
        String presented = header.substring("Bearer ".length());
        VerifiedIntegrationAuthorization verified = verificationService.verify(presented).orElse(null);
        if (verified == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        if (!ALLOWLIST.matches(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return;
        }

        IntegrationPrincipal principal = new IntegrationPrincipal(verified.authorizationId(), verified.agentId(),
                verified.workspaceId(), verified.authorizedByUserId());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(IntegrationAuthenticationToken.verified(principal));
        SecurityContextHolder.setContext(context);
        request.setAttribute(CSRF_BYPASS_ATTRIBUTE, Boolean.TRUE);
        try {
            usageTouchService.touch(verified.authorizationId(), clock.instant());
        } catch (RuntimeException failure) {
            LOGGER.warn("Integration authorization usage timestamp update failed.");
        }
        chain.doFilter(request, response);
    }
}
