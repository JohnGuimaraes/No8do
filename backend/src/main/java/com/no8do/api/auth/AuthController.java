package com.no8do.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PersonalApiTokenService personalApiTokenService;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AuthController(AuthService authService, PersonalApiTokenService personalApiTokenService) {
        this.authService = authService;
        this.personalApiTokenService = personalApiTokenService;
    }

    @PostMapping("/register")
    public AuthUserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return authService.register(request, httpRequest);
    }

    @PostMapping("/login")
    public AuthUserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest);
    }

    @PostMapping("/password/forgot")
    public Map<String, String> forgotPassword(@Valid @RequestBody PasswordForgotRequest request) {
        return authService.requestPasswordReset(request);
    }

    @PostMapping("/password/reset")
    public Map<String, String> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        return authService.resetPassword(request);
    }

    @PostMapping("/api-tokens")
    public CreatedPersonalApiTokenResponse createApiToken(Authentication authentication, @Valid @RequestBody CreatePersonalApiTokenRequest request) {
        return personalApiTokenService.create(currentUserId(authentication), request);
    }

    @GetMapping("/api-tokens")
    public List<PersonalApiTokenResponse> listApiTokens(Authentication authentication) {
        return personalApiTokenService.list(currentUserId(authentication));
    }

    @DeleteMapping("/api-tokens/{tokenId}")
    public void revokeApiToken(Authentication authentication, @org.springframework.web.bind.annotation.PathVariable UUID tokenId) {
        personalApiTokenService.revoke(currentUserId(authentication), tokenId);
    }

    @GetMapping("/google")
    public void startGoogleLogin(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        authService.startGoogleLogin(request, response);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        logoutHandler.logout(request, response, authentication);
        return Map.of("status", "ok");
    }

    @GetMapping("/me")
    public AuthUserResponse me(Authentication authentication) {
        return authService.me(authentication);
    }

    @PatchMapping("/me")
    public AuthUserResponse updateMe(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateCurrentUser(authentication, request);
    }

    @DeleteMapping("/me")
    public Map<String, String> deleteMe(
            Authentication authentication,
            @Valid @RequestBody DeleteAccountRequest deleteRequest,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        authService.deleteCurrentUser(authentication, deleteRequest);
        logoutHandler.logout(httpRequest, response, authentication);
        return Map.of("status", "ok");
    }

    private UUID currentUserId(Authentication authentication) {
        return ((No8doUserDetails) authentication.getPrincipal()).user().getId();
    }
}
