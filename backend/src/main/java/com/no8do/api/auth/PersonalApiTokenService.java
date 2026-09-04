package com.no8do.api.auth;

import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PersonalApiTokenService {
    public static final String CSRF_BYPASS_ATTRIBUTE = PersonalApiTokenService.class.getName() + ".csrfBypass";
    private static final String PREFIX = "no8do_pat_";
    private final PersonalApiTokenRepository repository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    public PersonalApiTokenService(PersonalApiTokenRepository repository, UserRepository userRepository) { this.repository = repository; this.userRepository = userRepository; }

    @Transactional
    public CreatedPersonalApiTokenResponse create(UUID userId, CreatePersonalApiTokenRequest request) {
        String name = normalizeName(request.name());
        String value = PREFIX + randomValue();
        PersonalApiToken saved = repository.save(new PersonalApiToken(user(userId), name, hash(value)));
        return new CreatedPersonalApiTokenResponse(PersonalApiTokenResponse.from(saved), value);
    }
    @Transactional(readOnly = true)
    public List<PersonalApiTokenResponse> list(UUID userId) { return repository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(PersonalApiTokenResponse::from).toList(); }
    @Transactional
    public void revoke(UUID userId, UUID tokenId) { repository.findByIdAndUserId(tokenId, userId).ifPresent(token -> { if (token.getRevokedAt() == null) token.revoke(); }); }
    @Transactional(readOnly = true)
    public User authenticate(String value) {
        if (value == null || !value.startsWith(PREFIX)) return null;
        return repository.findActiveByTokenHash(hash(value)).map(PersonalApiToken::getUser).filter(User::isEnabled).orElse(null);
    }
    private User user(UUID userId) { return userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required")); }
    private String normalizeName(String name) { if (name == null || name.trim().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API token name is required"); String value = name.trim(); if (value.length() > 160) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API token name is too long"); return value; }
    private String randomValue() { byte[] bytes = new byte[32]; secureRandom.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hash(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); } }
}
