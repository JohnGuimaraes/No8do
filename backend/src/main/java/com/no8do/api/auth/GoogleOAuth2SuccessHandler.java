package com.no8do.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final GoogleOAuthIdentityService googleOAuthIdentityService;
    private final String frontendUrl;

    public GoogleOAuth2SuccessHandler(
            GoogleOAuthIdentityService googleOAuthIdentityService,
            @Value("${no8do.frontend-url}") String frontendUrl
    ) {
        this.googleOAuthIdentityService = googleOAuthIdentityService;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        try {
            if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
                redirectToLogin(response, "google");
                return;
            }

            Authentication no8doAuthentication = new UsernamePasswordAuthenticationToken(
                googleOAuthIdentityService.resolve(oidcUser),
                null,
                List.of()
            );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(no8doAuthentication);
            SecurityContextHolder.setContext(context);
            request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            response.sendRedirect(frontendUrl.replaceAll("/+$", ""));
        } catch (GoogleAccountLinkRequiredException exception) {
            redirectToLogin(response, "google-account-exists");
        } catch (GoogleRegistrationDisabledException exception) {
            redirectToLogin(response, "google-registration-disabled");
        } catch (RuntimeException exception) {
            redirectToLogin(response, "google");
        }
    }

    private void redirectToLogin(HttpServletResponse response, String error) throws IOException {
        response.sendRedirect(frontendUrl.replaceAll("/+$", "") + "/?authError=" + error);
    }
}
