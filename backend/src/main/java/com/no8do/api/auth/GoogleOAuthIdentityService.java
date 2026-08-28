package com.no8do.api.auth;

import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GoogleOAuthIdentityService {

    private final UserRepository userRepository;
    private final UserExternalIdentityRepository userExternalIdentityRepository;
    private final ObjectProvider<PasswordEncoder> passwordEncoder;
    private final boolean registrationEnabled;
    private final SecureRandom secureRandom = new SecureRandom();

    public GoogleOAuthIdentityService(
            UserRepository userRepository,
            UserExternalIdentityRepository userExternalIdentityRepository,
            ObjectProvider<PasswordEncoder> passwordEncoder,
            @Value("${no8do.registration.enabled:true}") boolean registrationEnabled
    ) {
        this.userRepository = userRepository;
        this.userExternalIdentityRepository = userExternalIdentityRepository;
        this.passwordEncoder = passwordEncoder;
        this.registrationEnabled = registrationEnabled;
    }

    @Transactional
    public UserDetails resolve(OidcUser oidcUser) {
        String subject = requiredClaim(oidcUser, "sub");
        return userExternalIdentityRepository.findByProviderAndProviderSubject(
                ExternalIdentityProvider.GOOGLE,
                subject
            )
            .<UserDetails>map(identity -> new No8doUserDetails(identity.getUser()))
            .orElseGet(() -> createIdentityForNewUser(oidcUser, subject));
    }

    private UserDetails createIdentityForNewUser(OidcUser oidcUser, String subject) {
        if (!registrationEnabled) {
            throw new GoogleRegistrationDisabledException();
        }

        if (!Boolean.TRUE.equals(oidcUser.getClaim("email_verified"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email is not verified");
        }

        String email = requiredClaim(oidcUser, "email").trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new GoogleAccountLinkRequiredException();
        }

        User user = userRepository.save(new User(displayName(oidcUser, email), email, randomPasswordHash()));
        userExternalIdentityRepository.save(new UserExternalIdentity(
            user,
            ExternalIdentityProvider.GOOGLE,
            subject
        ));
        return new No8doUserDetails(user);
    }

    private String requiredClaim(OidcUser oidcUser, String name) {
        String value = oidcUser.getClaimAsString(name);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google identity is invalid");
        }
        return value;
    }

    private String displayName(OidcUser oidcUser, String email) {
        String name = oidcUser.getClaimAsString("name");
        String result = name == null || name.isBlank() ? email.substring(0, email.indexOf('@')) : name.trim();
        return result.length() > 160 ? result.substring(0, 160) : result;
    }

    private String randomPasswordHash() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return passwordEncoder.getObject().encode(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }
}
