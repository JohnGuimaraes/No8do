package com.no8do.api.agent;

import com.no8do.api.auth.No8doUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.method.HandlerMethod;
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
    private final AgentSessionPresenceService presenceService;

    public AgentSessionContextInterceptor(AgentSessionContextResolver contextResolver,
            AgentSessionPresenceService presenceService) {
        this.contextResolver = contextResolver;
        this.presenceService = presenceService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (isSessionLifecycleEndpoint(handler) || isAgentEventStreamEndpoint(handler)) return true;
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
        if (context.disconnectedAt() != null) {
            if (isGetContext(handler)) {
                request.setAttribute(AgentSessionContextResolver.REQUEST_ATTRIBUTE, context);
                return true;
            }
            throw new AgentSessionDisconnectedException(sessionId);
        }
        presenceService.touchActivity(sessionId, principal.user().getId());
        context = contextResolver.resolve(sessionId, principal.user().getId());
        if (context.disconnectedAt() != null) throw new AgentSessionDisconnectedException(sessionId);
        request.setAttribute(AgentSessionContextResolver.REQUEST_ATTRIBUTE, context);
        return true;
    }

    private boolean isSessionLifecycleEndpoint(Object handler) {
        return handler instanceof HandlerMethod method
                && AgentSessionController.class.isAssignableFrom(method.getBeanType())
                && (method.getMethod().getName().equals("heartbeat")
                    || method.getMethod().getName().equals("disconnect"));
    }

    private boolean isGetContext(Object handler) {
        return handler instanceof HandlerMethod method
                && AgentSessionController.class.isAssignableFrom(method.getBeanType())
                && method.getMethod().getName().equals("getContext");
    }

    private boolean isAgentEventStreamEndpoint(Object handler) {
        return handler instanceof HandlerMethod method
                && AgentEventStreamController.class.isAssignableFrom(method.getBeanType());
    }
}
