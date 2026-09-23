package com.no8do.api.agent;

import com.no8do.api.auth.No8doUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.server.ResponseStatusException;

@Component
public final class AgentSessionContextInterceptor implements HandlerInterceptor {
    public static final String HEADER_NAME = "X-No8do-Agent-Session-Id";

    private final AgentSessionContextResolver contextResolver;

    public AgentSessionContextInterceptor(AgentSessionContextResolver contextResolver) {
        this.contextResolver = contextResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String rawSessionId = request.getHeader(HEADER_NAME);
        if (rawSessionId == null) return true;
        if (rawSessionId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid agent session id");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof No8doUserDetails principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required for agent session context");
        }

        UUID sessionId;
        try {
            sessionId = UUID.fromString(rawSessionId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid agent session id");
        }
        AgentSessionContext context = contextResolver.resolve(sessionId, principal.user().getId());
        request.setAttribute(AgentSessionContextResolver.REQUEST_ATTRIBUTE, context);
        return true;
    }
}
