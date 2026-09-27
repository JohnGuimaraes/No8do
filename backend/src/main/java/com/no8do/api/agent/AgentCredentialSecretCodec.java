package com.no8do.api.agent;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Encodes show-once credentials as no8do_ac1.&lt;public-id&gt;.&lt;secret&gt;. */
@Component
public class AgentCredentialSecretCodec {

    private static final String FORMAT_VERSION = "no8do_ac1";
    private static final int PUBLIC_ID_BYTES = 16;
    private static final int SECRET_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    IssuedCredential issue() {
        byte[] publicId = new byte[PUBLIC_ID_BYTES];
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(publicId);
        secureRandom.nextBytes(secret);
        String publicCredentialId = encode(publicId);
        String secretValue = encode(secret);
        return new IssuedCredential(publicCredentialId, secretValue, hash(secret));
    }

    Optional<ParsedCredential> parse(String presented) {
        if (presented == null || presented.length() > 128) return Optional.empty();
        String[] parts = presented.split("\\.", -1);
        if (parts.length != 3 || !FORMAT_VERSION.equals(parts[0])) return Optional.empty();
        try {
            byte[] publicId = Base64.getUrlDecoder().decode(parts[1]);
            byte[] secret = Base64.getUrlDecoder().decode(parts[2]);
            if (publicId.length != PUBLIC_ID_BYTES || secret.length != SECRET_BYTES
                    || !encode(publicId).equals(parts[1]) || !encode(secret).equals(parts[2])) {
                return Optional.empty();
            }
            return Optional.of(new ParsedCredential(parts[1], secret));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    boolean matches(String storedHash, byte[] secret) {
        byte[] expected = new byte[32];
        try {
            if (storedHash != null) expected = HexFormat.of().parseHex(storedHash);
        } catch (IllegalArgumentException exception) {
            // Use the dummy digest for malformed stored values and still perform a constant-time comparison.
        }
        return MessageDigest.isEqual(expected, digest(secret));
    }

    private static String hash(byte[] secret) {
        return HexFormat.of().formatHex(digest(secret));
    }

    private static byte[] digest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    static final class IssuedCredential {
        private final String publicCredentialId;
        private final String secret;
        private final String secretHash;

        private IssuedCredential(String publicCredentialId, String secret, String secretHash) {
            this.publicCredentialId = publicCredentialId;
            this.secret = secret;
            this.secretHash = secretHash;
        }

        String publicCredentialId() { return publicCredentialId; }
        String secretHash() { return secretHash; }
        String serialized() { return FORMAT_VERSION + "." + publicCredentialId + "." + secret; }

        @Override
        public String toString() {
            return "IssuedCredential[REDACTED]";
        }
    }

    static final class ParsedCredential {
        private final String publicCredentialId;
        private final byte[] secret;

        private ParsedCredential(String publicCredentialId, byte[] secret) {
            this.publicCredentialId = publicCredentialId;
            this.secret = secret;
        }

        String publicCredentialId() { return publicCredentialId; }
        byte[] secret() { return secret; }
    }
}
