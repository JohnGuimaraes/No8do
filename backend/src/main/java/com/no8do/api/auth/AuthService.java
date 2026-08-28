package com.no8do.api.auth;

import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetEmailService passwordResetEmailService;
    private final GoogleOAuthConfiguration googleOAuthConfiguration;
    private final String frontendUrl;
    private final boolean registrationEnabled;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            AuthenticationManager authenticationManager,
            PasswordEncoder passwordEncoder,
            UserRepository userRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordResetEmailService passwordResetEmailService,
            GoogleOAuthConfiguration googleOAuthConfiguration,
            @Value("${no8do.frontend-url}") String frontendUrl,
            @Value("${no8do.registration.enabled:true}") boolean registrationEnabled
    ) {
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordResetEmailService = passwordResetEmailService;
        this.googleOAuthConfiguration = googleOAuthConfiguration;
        this.frontendUrl = frontendUrl;
        this.registrationEnabled = registrationEnabled;
    }

    @Transactional
    public AuthUserResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        if (!registrationEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Registration is disabled");
        }

        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()));
        User savedUser = userRepository.save(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            new No8doUserDetails(savedUser),
            null,
            List.of()
        );
        storeAuthentication(authentication, httpRequest);
        return AuthUserResponse.from(savedUser);
    }

    public AuthUserResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                    normalizeEmail(request.email()),
                    request.password()
                )
            );
            storeAuthentication(authentication, httpRequest);
            return currentUser(authentication);
        } catch (BadCredentialsException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
    }

    public AuthUserResponse establishSession(User user, HttpServletRequest httpRequest) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(new No8doUserDetails(user), null, List.of());
        storeAuthentication(authentication, httpRequest);
        return AuthUserResponse.from(user);
    }

    public AuthUserResponse me(Authentication authentication) {
        return currentUser(authentication);
    }

    @Transactional
    public void deleteCurrentUser(Authentication authentication, DeleteAccountRequest request) {
        User user = currentUserEntity(authentication);
        validateAccountDeletionConfirmation(user, request);
        if (workspaceMemberRepository.existsByUserIdAndRole(user.getId(), WorkspaceRole.OWNER)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delete owned workspaces before deleting your account");
        }
        userRepository.delete(user);
        userRepository.flush();
    }

    @Transactional
    public Map<String, String> requestPasswordReset(PasswordForgotRequest request) {
        if (!passwordResetEmailService.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Password reset is temporarily unavailable");
        }

        String email = normalizeEmail(request.email());
        userRepository.findByEmail(email).ifPresent(user -> createAndSendPasswordResetToken(user));
        return Map.of("status", "ok");
    }

    @Transactional
    public Map<String, String> resetPassword(PasswordResetRequest request) {
        Instant now = Instant.now();
        PasswordResetToken token = passwordResetTokenRepository
            .findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(hashToken(request.token()), now)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password reset token is invalid or expired"));

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        token.markUsed(now);
        passwordResetTokenRepository.invalidateUnusedForUser(user.getId(), now);
        return Map.of("status", "ok");
    }

    public void startGoogleLogin(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        if (!googleOAuthConfiguration.isConfigured()) {
            response.sendRedirect(frontendUrl.replaceAll("/+$", "") + "/?authError=google-unavailable");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/api/oauth2/authorization/google");
    }

    private AuthUserResponse currentUser(Authentication authentication) {
        return AuthUserResponse.from(currentUserEntity(authentication));
    }

    private User currentUserEntity(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof No8doUserDetails userDetails)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return userRepository.findById(userDetails.user().getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    private void validateAccountDeletionConfirmation(User user, DeleteAccountRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account deletion confirmation is required");
        }
        String confirmationEmail = normalizeEmail(request.confirmationEmail());
        if (!user.getEmail().equals(confirmationEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account deletion email does not match");
        }
        if (!"EXCLUIR".equals(request.confirmationText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account deletion confirmation text does not match");
        }
    }

    private void storeAuthentication(Authentication authentication, HttpServletRequest httpRequest) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        httpRequest.getSession(true).setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            SecurityContextHolder.getContext()
        );
    }

    private void createAndSendPasswordResetToken(User user) {
        Instant now = Instant.now();
        passwordResetTokenRepository.invalidateUnusedForUser(user.getId(), now);

        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        PasswordResetToken passwordResetToken = new PasswordResetToken(
            user,
            hashToken(token),
            now.plus(Duration.ofHours(1))
        );
        passwordResetTokenRepository.save(passwordResetToken);
        passwordResetEmailService.sendResetLink(user, token);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
