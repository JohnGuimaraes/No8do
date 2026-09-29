package com.no8do.api.integration;

import com.no8do.api.auth.No8doUserDetails;
import com.no8do.api.auth.PersonalApiTokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HumanSessionOnly {
    public UUID requireUserId(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof No8doUserDetails principal)
                || Boolean.TRUE.equals(request.getAttribute(PersonalApiTokenService.CSRF_BYPASS_ATTRIBUTE))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A human browser session is required");
        }
        return principal.user().getId();
    }
}
