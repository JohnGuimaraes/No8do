package com.no8do.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@SpringBootTest(properties = "no8do.registration.enabled=true")
class GoogleOAuthIdentityServiceTests {

    @Autowired
    private GoogleOAuthIdentityService googleOAuthIdentityService;

    @Autowired
    private UserExternalIdentityRepository userExternalIdentityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void firstGoogleLoginCreatesUserAndIdentityAndSecondLoginReusesIt() {
        String subject = "google-subject-" + UUID.randomUUID();
        OidcUser googleUser = googleUser(subject, uniqueEmail());

        UserDetails firstLogin = googleOAuthIdentityService.resolve(googleUser);
        UserDetails secondLogin = googleOAuthIdentityService.resolve(googleUser);

        assertThat(userExternalIdentityRepository
            .findByProviderAndProviderSubject(ExternalIdentityProvider.GOOGLE, subject)).isPresent();
        User persistedUser = userRepository.findByEmail(firstLogin.getUsername()).orElseThrow();
        assertThat(persistedUser.getEmail()).isEqualTo(firstLogin.getUsername());
        assertThat(secondLogin.getUsername()).isEqualTo(firstLogin.getUsername());
        assertThat(userRepository.findByEmail(firstLogin.getUsername()).map(User::getId)).contains(persistedUser.getId());
        assertThat(passwordEncoder.matches("senha-conhecida", persistedUser.getPasswordHash())).isFalse();
    }

    @Test
    void localAccountWithMatchingEmailIsNotLinkedAutomatically() {
        String email = uniqueEmail();
        userRepository.save(new User("Local User", email, passwordEncoder.encode("senha-correta")));

        String subject = "google-" + UUID.randomUUID();
        assertThatThrownBy(() -> googleOAuthIdentityService.resolve(googleUser(subject, email)))
            .isInstanceOf(GoogleAccountLinkRequiredException.class);
        assertThat(userExternalIdentityRepository
            .findByProviderAndProviderSubject(ExternalIdentityProvider.GOOGLE, subject)).isEmpty();
    }

    @Test
    void providerSubjectIsUnique() {
        String subject = "google-subject-" + UUID.randomUUID();
        googleOAuthIdentityService.resolve(googleUser(subject, uniqueEmail()));
        User otherUser = userRepository.save(new User("Other User", uniqueEmail(), passwordEncoder.encode("senha-correta")));

        assertThatThrownBy(() -> userExternalIdentityRepository.saveAndFlush(new UserExternalIdentity(
            otherUser,
            ExternalIdentityProvider.GOOGLE,
            subject
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    private OidcUser googleUser(String subject, String email) {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
            "id-token-" + UUID.randomUUID(),
            now,
            now.plusSeconds(3600),
            Map.of(
                "sub", subject,
                "email", email,
                "email_verified", true,
                "name", "Google User"
            )
        );
        return new DefaultOidcUser(List.of(), idToken);
    }

    private String uniqueEmail() {
        return "google-" + UUID.randomUUID() + "@example.com";
    }
}
