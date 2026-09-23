package com.no8do.api.agent;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AgentCapabilityAuthorizationService {
    public void require(AgentCapability capability) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) return;
        HttpServletRequest request = servletAttributes.getRequest();
        Object contextValue = request.getAttribute(AgentSessionContextResolver.REQUEST_ATTRIBUTE);
        if (!(contextValue instanceof AgentSessionContext context)) return;
        if (!context.effectiveCapabilities().contains(capability)) {
            throw new AgentCapabilityDeniedException(context.session(), capability);
        }
    }
}
