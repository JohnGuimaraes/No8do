package com.no8do.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class IntegrationCredentialCodecTests {
    private static final String TEST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY";

    @Test
    void integrationCredentialIsDistinctStrictShowOnceAndHashOnly() {
        IntegrationBootstrapCrypto crypto = new IntegrationBootstrapCrypto(true, TEST_KEY);
        crypto.validateConfiguration();
        IntegrationCredentialCodec codec = new IntegrationCredentialCodec(crypto);
        IntegrationCredentialCodec.IssuedIntegrationCredential issued = codec.issue();
        String token = issued.serialized();
        String[] pieces = token.substring("no8do_int_".length()).split("\\.");
        assertThat(token).startsWith("no8do_int_").doesNotStartWith("no8do_pat_");
        assertThat(pieces).hasSize(2);
        assertThat(Base64.getUrlDecoder().decode(pieces[0])).hasSize(16);
        assertThat(Base64.getUrlDecoder().decode(pieces[1])).hasSize(32);
        assertThat(issued.tokenHash()).hasSize(64).doesNotContain(token, pieces[1]);
        assertThat(codec.parse(token)).isPresent();
        assertThat(codec.matches(issued.tokenHash(), codec.parse(token).orElseThrow().secret())).isTrue();
        assertThat(codec.parse(token + ".extra")).isEmpty();
        assertThat(issued.toString()).doesNotContain(token, pieces[1], issued.tokenHash());
    }

    @Test
    void pkceUsesCanonicalS256AndEnabledBootstrapRequiresAValidHmacKey() {
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                IntegrationBootstrapCrypto.sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        assertThat(IntegrationCredentialCodec.validChallenge(challenge)).isTrue();
        assertThat(IntegrationCredentialCodec.verifierMatches(verifier, challenge)).isTrue();
        assertThat(IntegrationCredentialCodec.verifierMatches(verifier + "x", challenge)).isFalse();
        assertThatThrownBy(() -> new IntegrationBootstrapCrypto(true, "invalid").validateConfiguration())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("HMAC key");
    }
}
