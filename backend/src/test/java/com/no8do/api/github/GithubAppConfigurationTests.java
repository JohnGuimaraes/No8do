package com.no8do.api.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class GithubAppConfigurationTests {

    @Test
    void installationFlowDoesNotRequirePrivateKeyOrWebhookSecret() {
        GithubAppConfiguration configuration = new GithubAppConfiguration(
            "123", "no8do", "", "", "", "client-id", "client-secret", "https://no8do.test/github/callback"
        );

        assertThat(configuration.isInstallationFlowConfigured()).isTrue();
    }

    @Test
    void resolvesAValidBase64EncodedRsaPem() throws Exception {
        String pem = validRsaPem();
        String encodedPem = Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.US_ASCII));

        assertThat(configuration(encodedPem, "").privateKey().getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void resolvesALegacyRsaPemWithEscapedNewlines() throws Exception {
        String escapedPem = validRsaPem().replace("\n", "\\n");

        assertThat(configuration("", escapedPem).privateKey().getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void resolvesARealPkcs1RsaPrivateKeyPem() throws Exception {
        RSAPrivateCrtKey originalKey = (RSAPrivateCrtKey) KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate();
        String pkcs1Pem = pkcs1Pem(originalKey);

        RSAPrivateKey resolvedKey = (RSAPrivateKey) configuration("", pkcs1Pem).privateKey();

        assertThat(resolvedKey.getModulus()).isEqualTo(originalKey.getModulus());
        assertThat(resolvedKey.getPrivateExponent()).isEqualTo(originalKey.getPrivateExponent());
    }

    @Test
    void resolvesPkcs1PemWithCrlfAndWhitespaceBetweenLines() throws Exception {
        RSAPrivateCrtKey originalKey = (RSAPrivateCrtKey) KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate();
        String multilinePem = pkcs1Pem(originalKey).replace("\n", "\r\n \t");

        RSAPrivateKey resolvedKey = (RSAPrivateKey) configuration("", multilinePem).privateKey();

        assertThat(resolvedKey.getModulus()).isEqualTo(originalKey.getModulus());
        assertThat(resolvedKey.getPrivateExponent()).isEqualTo(originalKey.getPrivateExponent());
    }

    @Test
    void rejectsAnInvalidBase64PrivateKey() {
        assertConfigurationMissing(configuration("not-base64!", ""));
    }

    @Test
    void rejectsAnInvalidPemPrivateKey() {
        String encodedPem = Base64.getEncoder().encodeToString("not a PEM".getBytes(StandardCharsets.US_ASCII));

        assertConfigurationMissing(configuration(encodedPem, ""));
    }

    @Test
    void rejectsAnAbsentPrivateKeyConfiguration() {
        assertConfigurationMissing(configuration("", ""));
    }

    private GithubAppConfiguration configuration(String privateKeyBase64, String legacyPrivateKey) {
        return new GithubAppConfiguration(
            "123", "no8do", privateKeyBase64, legacyPrivateKey, "", "client-id", "client-secret", "https://no8do.test/github/callback"
        );
    }

    private String validRsaPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----";
    }

    private String pkcs1Pem(RSAPrivateCrtKey key) {
        byte[] der = sequence(
            integer(java.math.BigInteger.ZERO),
            integer(key.getModulus()),
            integer(key.getPublicExponent()),
            integer(key.getPrivateExponent()),
            integer(key.getPrimeP()),
            integer(key.getPrimeQ()),
            integer(key.getPrimeExponentP()),
            integer(key.getPrimeExponentQ()),
            integer(key.getCrtCoefficient())
        );
        return "-----BEGIN RSA PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der)
            + "\n-----END RSA PRIVATE KEY-----";
    }

    private byte[] sequence(byte[]... values) {
        return der(0x30, concatenate(values));
    }

    private byte[] integer(java.math.BigInteger value) {
        return der(0x02, value.toByteArray());
    }

    private byte[] der(int tag, byte[] value) {
        byte[] length = length(value.length);
        byte[] result = new byte[1 + length.length + value.length];
        result[0] = (byte) tag;
        System.arraycopy(length, 0, result, 1, length.length);
        System.arraycopy(value, 0, result, 1 + length.length, value.length);
        return result;
    }

    private byte[] length(int value) {
        if (value < 128) return new byte[] {(byte) value};
        int size = Integer.BYTES - Integer.numberOfLeadingZeros(value) / Byte.SIZE;
        byte[] result = new byte[size + 1];
        result[0] = (byte) (0x80 | size);
        for (int index = size; index > 0; index--) {
            result[index] = (byte) value;
            value >>>= Byte.SIZE;
        }
        return result;
    }

    private byte[] concatenate(byte[]... values) {
        int size = java.util.Arrays.stream(values).mapToInt(value -> value.length).sum();
        byte[] result = new byte[size];
        int offset = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, result, offset, value.length);
            offset += value.length;
        }
        return result;
    }

    private void assertConfigurationMissing(GithubAppConfiguration configuration) {
        assertThatThrownBy(configuration::privateKey)
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            );
    }
}
