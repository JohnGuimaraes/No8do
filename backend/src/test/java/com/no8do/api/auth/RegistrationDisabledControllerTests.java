package com.no8do.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "no8do.registration.enabled=false")
@AutoConfigureMockMvc
class RegistrationDisabledControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GoogleOAuthIdentityService googleOAuthIdentityService;

    @Autowired
    private UserExternalIdentityRepository userExternalIdentityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registerIsRejectedWhenRegistrationIsDisabled() throws Exception {
        RegisterRequest request = new RegisterRequest("Ana No8do", "ana@example.com", "senha-segura");

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("Registration is disabled"));
    }

    @Test
    void existingGoogleIdentityCanLogInWhenRegistrationIsDisabled() {
        String subject = "google-subject-" + UUID.randomUUID();
        User user = userRepository.save(new User("Google User", uniqueEmail(), passwordEncoder.encode("senha-local")));
        userExternalIdentityRepository.save(new UserExternalIdentity(user, ExternalIdentityProvider.GOOGLE, subject));

        assertThat(googleOAuthIdentityService.resolve(googleUser(subject, user.getEmail())).getUsername())
            .isEqualTo(user.getEmail());
    }

    @Test
    void newGoogleIdentityCannotCreateUserWhenRegistrationIsDisabled() {
        String email = uniqueEmail();

        assertThatThrownBy(() -> googleOAuthIdentityService.resolve(googleUser("google-" + UUID.randomUUID(), email)))
            .isInstanceOf(GoogleRegistrationDisabledException.class);
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    private DefaultOidcUser googleUser(String subject, String email) {
        Instant now = Instant.now();
        return new DefaultOidcUser(List.of(), new OidcIdToken(
            "id-token-" + UUID.randomUUID(),
            now,
            now.plusSeconds(3600),
            Map.of("sub", subject, "email", email, "email_verified", true, "name", "Google User")
        ));
    }

    private String uniqueEmail() {
        return "google-registration-" + UUID.randomUUID() + "@example.com";
    }
}
