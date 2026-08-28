package com.no8do.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@SpringBootTest
class GoogleOAuth2HandlerTests {

    @Autowired
    private GoogleOAuth2SuccessHandler googleOAuth2SuccessHandler;

    @Autowired
    private GoogleOAuth2FailureHandler googleOAuth2FailureHandler;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulGoogleLoginStoresNo8doAuthenticationInTheHttpSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        googleOAuth2SuccessHandler.onAuthenticationSuccess(
            request,
            response,
            new UsernamePasswordAuthenticationToken(googleUser(), null, List.of())
        );

        SecurityContext context = (SecurityContext) request.getSession(false)
            .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context).isNotNull();
        assertThat(context.getAuthentication().getPrincipal()).isInstanceOf(No8doUserDetails.class);
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173");
    }

    @Test
    void oauthFailureRedirectDoesNotExposeProviderDetails() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        googleOAuth2FailureHandler.onAuthenticationFailure(
            new MockHttpServletRequest(),
            response,
            new OAuth2AuthenticationException(new OAuth2Error("access_denied", "sensitive provider detail", null))
        );

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/?authError=google");
        assertThat(response.getRedirectedUrl()).doesNotContain("sensitive");
    }

    private OidcUser googleUser() {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
            "id-token-" + UUID.randomUUID(),
            now,
            now.plusSeconds(3600),
            Map.of(
                "sub", "google-handler-" + UUID.randomUUID(),
                "email", "google-handler-" + UUID.randomUUID() + "@example.com",
                "email_verified", true,
                "name", "Google Handler"
            )
        );
        return new DefaultOidcUser(List.of(), idToken);
    }
}
