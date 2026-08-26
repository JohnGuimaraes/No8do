package com.no8do.api.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class GithubAppJwtGeneratorTests {

    @Test
    void generatesASignedRsaJwtForTheGithubApp() throws Exception {
        KeyPair keyPair = keyPair();
        GithubAppJwtGenerator generator = new GithubAppJwtGenerator(Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC));

        String jwt = generator.generate(configuration(keyPair));

        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        assertThat(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8))
            .contains("\"iss\":\"123\"", "\"iat\":1787572740", "\"exp\":1787573340");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    @Test
    void rejectsAnAbsentPrivateKeyConfiguration() {
        GithubAppConfiguration configuration = new GithubAppConfiguration(
            "123", "no8do", "", "", "", "client-id", "client-secret", "https://no8do.test/callback"
        );

        assertThatThrownBy(() -> new GithubAppJwtGenerator().generate(configuration))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            );
    }

    static GithubAppConfiguration configuration(KeyPair keyPair) {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(keyPair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----";
        return new GithubAppConfiguration(
            "123", "no8do", "", pem, "", "client-id", "client-secret", "https://no8do.test/callback"
        );
    }

    static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
