package com.no8do.api.integration;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class IntegrationCredentialCodec {
    private static final String PREFIX = "no8do_int_";
    private final IntegrationBootstrapCrypto crypto;

    public IntegrationCredentialCodec(IntegrationBootstrapCrypto crypto) { this.crypto = crypto; }

    IssuedIntegrationCredential issue() {
        byte[] selector = crypto.randomBytes(16);
        byte[] secret = crypto.randomBytes(32);
        String encodedSelector = encode(selector);
        String encodedSecret = encode(secret);
        return new IssuedIntegrationCredential(encodedSelector, hash(secret), PREFIX + encodedSelector + "." + encodedSecret);
    }

    Optional<ParsedIntegrationCredential> parse(String presented) {
        if (presented == null || presented.length() > 100 || !presented.startsWith(PREFIX)) return Optional.empty();
        String[] parts = presented.substring(PREFIX.length()).split("\\.", -1);
        if (parts.length != 2) return Optional.empty();
        try {
            byte[] selector = Base64.getUrlDecoder().decode(parts[0]);
            byte[] secret = Base64.getUrlDecoder().decode(parts[1]);
            if (selector.length != 16 || secret.length != 32 || !encode(selector).equals(parts[0])
                    || !encode(secret).equals(parts[1])) return Optional.empty();
            return Optional.of(new ParsedIntegrationCredential(parts[0], secret));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    boolean matches(String storedHash, byte[] secret) {
        byte[] expected = new byte[32];
        try { if (storedHash != null) expected = HexFormat.of().parseHex(storedHash); }
        catch (IllegalArgumentException ignored) { /* Keep the dummy digest for malformed stored data. */ }
        return MessageDigest.isEqual(expected, IntegrationBootstrapCrypto.sha256(secret));
    }

    static String hash(byte[] secret) { return IntegrationBootstrapCrypto.hex(IntegrationBootstrapCrypto.sha256(secret)); }

    static boolean validChallenge(String challenge) {
        if (challenge == null || challenge.length() != 43) return false;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(challenge);
            return decoded.length == 32 && encode(decoded).equals(challenge);
        } catch (IllegalArgumentException exception) { return false; }
    }

    static boolean verifierMatches(String verifier, String challenge) {
        if (verifier == null || verifier.length() < 43 || verifier.length() > 128
                || !verifier.matches("[A-Za-z0-9._~-]+") || !validChallenge(challenge)) return false;
        byte[] actual = IntegrationBootstrapCrypto.sha256(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        byte[] expected = Base64.getUrlDecoder().decode(challenge);
        return MessageDigest.isEqual(expected, actual);
    }

    private static String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }

    static final class IssuedIntegrationCredential {
        private final String selector;
        private final String tokenHash;
        private final String serialized;
        private IssuedIntegrationCredential(String selector, String tokenHash, String serialized) {
            this.selector = selector; this.tokenHash = tokenHash; this.serialized = serialized;
        }
        String selector() { return selector; }
        String tokenHash() { return tokenHash; }
        String serialized() { return serialized; }
        @Override public String toString() { return "IssuedIntegrationCredential[REDACTED]"; }
    }

    static final class ParsedIntegrationCredential {
        private final String selector;
        private final byte[] secret;
        private ParsedIntegrationCredential(String selector, byte[] secret) { this.selector = selector; this.secret = secret; }
        String selector() { return selector; }
        byte[] secret() { return secret; }
        @Override public String toString() { return "ParsedIntegrationCredential[REDACTED]"; }
    }
}
