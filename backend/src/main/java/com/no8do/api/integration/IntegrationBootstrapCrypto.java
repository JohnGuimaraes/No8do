package com.no8do.api.integration;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IntegrationBootstrapCrypto {
    static final String USER_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private final boolean enabled;
    private final String encodedHmacKey;
    private final SecureRandom secureRandom = new SecureRandom();
    private byte[] hmacKey;

    public IntegrationBootstrapCrypto(
            @Value("${no8do.integration.bootstrap.enabled:false}") boolean enabled,
            @Value("${no8do.integration.bootstrap.hmac-key:}") String encodedHmacKey) {
        this.enabled = enabled;
        this.encodedHmacKey = encodedHmacKey;
    }

    @PostConstruct
    void validateConfiguration() {
        if (!enabled) return;
        try {
            hmacKey = Base64.getUrlDecoder().decode(encodedHmacKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Integration bootstrap is enabled but its HMAC key is invalid");
        }
        if (hmacKey.length < 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(hmacKey).equals(encodedHmacKey)) {
            hmacKey = null;
            throw new IllegalStateException("Integration bootstrap requires a base64url HMAC key of at least 256 bits");
        }
    }

    public boolean isEnabled() { return enabled; }

    byte[] randomBytes(int size) {
        byte[] value = new byte[size];
        secureRandom.nextBytes(value);
        return value;
    }

    String randomDeviceCode() { return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32)); }

    String randomUserCode() {
        byte[] bytes = randomBytes(8);
        StringBuilder result = new StringBuilder(8);
        for (byte value : bytes) result.append(USER_CODE_ALPHABET.charAt((value & 0xff) % USER_CODE_ALPHABET.length()));
        return result.toString();
    }

    String hashDeviceCode(String value) { return hex(sha256(value.getBytes(StandardCharsets.UTF_8))); }

    String userCodeHmac(String value) {
        if (hmacKey == null) throw new IllegalStateException("Integration bootstrap HMAC is not configured");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return hex(mac.doFinal(normalizeUserCode(value).getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable");
        }
    }

    static String normalizeUserCode(String value) {
        if (value == null) return "";
        return value.replace("-", "").replace(" ", "").trim().toUpperCase(java.util.Locale.ROOT);
    }

    static boolean validUserCode(String value) {
        String normalized = normalizeUserCode(value);
        return normalized.length() == 8 && normalized.chars().allMatch(ch -> USER_CODE_ALPHABET.indexOf(ch) >= 0);
    }

    static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable"); }
    }

    static String hex(byte[] bytes) { return java.util.HexFormat.of().formatHex(bytes); }

    @Override public String toString() { return "IntegrationBootstrapCrypto[enabled=" + enabled + ", hmacKey=REDACTED]"; }
}
