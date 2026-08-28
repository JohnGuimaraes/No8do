package com.no8do.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GoogleOAuthIdentityService googleOAuthIdentityService;

    @MockBean
    private PasswordResetEmailService passwordResetEmailService;

    @BeforeEach
    void setUp() {
        when(passwordResetEmailService.isConfigured()).thenReturn(true);
    }

    @Test
    void forgotPasswordReturnsTheSameResponseForExistingAndUnknownEmail() throws Exception {
        User user = saveUser();
        PasswordResetToken previousToken = passwordResetTokenRepository.save(new PasswordResetToken(
            user,
            hash("previous-token-" + UUID.randomUUID()),
            Instant.now().plusSeconds(3600)
        ));

        MvcResult existingResponse = forgot(user.getEmail());
        MvcResult unknownResponse = forgot(uniqueEmail());

        assertThat(existingResponse.getResponse().getContentAsString())
            .isEqualTo(unknownResponse.getResponse().getContentAsString());
        assertThat(passwordResetTokenRepository.findById(previousToken.getId()).orElseThrow().getUsedAt()).isNotNull();

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordResetEmailService).sendResetLink(any(User.class), tokenCaptor.capture());
        String rawToken = tokenCaptor.getValue();
        PasswordResetToken persistedToken = passwordResetTokenRepository.findAll().stream()
            .filter(token -> token.getUser().getId().equals(user.getId()) && token.getUsedAt() == null)
            .findFirst()
            .orElseThrow();
        assertThat(persistedToken.getTokenHash()).isEqualTo(hash(rawToken));
        assertThat(persistedToken.getTokenHash()).isNotEqualTo(rawToken);
    }

    @Test
    void validTokenChangesPasswordWithBcryptAndCannotBeReused() throws Exception {
        User user = saveUser();
        String rawToken = "valid-reset-token-" + UUID.randomUUID();
        PasswordResetToken token = passwordResetTokenRepository.save(new PasswordResetToken(
            user,
            hash(rawToken),
            Instant.now().plusSeconds(3600)
        ));
        PasswordResetRequest request = new PasswordResetRequest(rawToken, "nova-senha-segura");

        mockMvc.perform(post("/api/auth/password/reset")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("nova-senha-segura", updatedUser.getPasswordHash())).isTrue();
        assertThat(updatedUser.getPasswordHash()).isNotEqualTo("nova-senha-segura");
        assertThat(passwordResetTokenRepository.findById(token.getId()).orElseThrow().getUsedAt()).isNotNull();

        mockMvc.perform(post("/api/auth/password/reset")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Password reset token is invalid or expired"));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        User user = saveUser();
        String rawToken = "expired-reset-token-" + UUID.randomUUID();
        passwordResetTokenRepository.save(new PasswordResetToken(
            user,
            hash(rawToken),
            Instant.now().minusSeconds(60)
        ));

        mockMvc.perform(post("/api/auth/password/reset")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PasswordResetRequest(rawToken, "nova-senha-segura"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Password reset token is invalid or expired"));
    }

    @Test
    void passwordResetLetsGoogleAccountEstablishALocalPasswordAfterEmailVerification() throws Exception {
        String email = uniqueEmail();
        googleOAuthIdentityService.resolve(googleUser(email));

        forgot(email);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordResetEmailService).sendResetLink(any(User.class), tokenCaptor.capture());

        mockMvc.perform(post("/api/auth/password/reset")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PasswordResetRequest(tokenCaptor.getValue(), "nova-senha-segura"))))
            .andExpect(status().isOk());

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(passwordEncoder.matches("nova-senha-segura", user.getPasswordHash())).isTrue();
    }

    private MvcResult forgot(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/password/forgot")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PasswordForgotRequest(email))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.token").doesNotExist())
            .andReturn();
    }

    private User saveUser() {
        return userRepository.save(new User("Password User", uniqueEmail(), passwordEncoder.encode("senha-correta")));
    }

    private String uniqueEmail() {
        return "password-reset-" + UUID.randomUUID() + "@example.com";
    }

    private DefaultOidcUser googleUser(String email) {
        Instant now = Instant.now();
        return new DefaultOidcUser(List.of(), new OidcIdToken(
            "id-token-" + UUID.randomUUID(),
            now,
            now.plusSeconds(3600),
            Map.of(
                "sub", "google-subject-" + UUID.randomUUID(),
                "email", email,
                "email_verified", true,
                "name", "Google User"
            )
        ));
    }

    private String hash(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
