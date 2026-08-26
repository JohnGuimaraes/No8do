package com.no8do.api.github;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GithubAppConfiguration {

    private static final String PKCS1_PEM_TYPE = "RSA PRIVATE KEY";
    private static final String PKCS8_PEM_TYPE = "PRIVATE KEY";
    private static final byte[] RSA_ALGORITHM_IDENTIFIER = {
        0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
        (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
    };

    private final String appId;
    private final String appSlug;
    private final String privateKeyBase64;
    private final String privateKey;
    private final String webhookSecret;
    private final String clientId;
    private final String clientSecret;
    private final String callbackUrl;

    public GithubAppConfiguration(
            @Value("${NO8DO_GITHUB_APP_ID:}") String appId,
            @Value("${NO8DO_GITHUB_APP_SLUG:}") String appSlug,
            @Value("${NO8DO_GITHUB_APP_PRIVATE_KEY_BASE64:}") String privateKeyBase64,
            @Value("${NO8DO_GITHUB_APP_PRIVATE_KEY:}") String privateKey,
            @Value("${NO8DO_GITHUB_APP_WEBHOOK_SECRET:}") String webhookSecret,
            @Value("${NO8DO_GITHUB_APP_CLIENT_ID:}") String clientId,
            @Value("${NO8DO_GITHUB_APP_CLIENT_SECRET:}") String clientSecret,
            @Value("${no8do.github.app.callback-url}") String callbackUrl
    ) {
        this.appId = appId;
        this.appSlug = appSlug;
        this.privateKeyBase64 = privateKeyBase64;
        this.privateKey = privateKey;
        this.webhookSecret = webhookSecret;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.callbackUrl = callbackUrl;
    }

    public boolean isInstallationFlowConfigured() {
        return !appId.isBlank() && !appSlug.isBlank()
            && !clientId.isBlank() && !clientSecret.isBlank() && !callbackUrl.isBlank();
    }

    public long appId() {
        try {
            return Long.parseLong(appId);
        } catch (NumberFormatException exception) {
            throw GithubAppClient.configurationMissing();
        }
    }

    public String appSlug() {
        return appSlug;
    }

    public String clientId() {
        return clientId;
    }

    public String clientSecret() {
        return clientSecret;
    }

    public String callbackUrl() {
        return callbackUrl;
    }

    public PrivateKey privateKey() {
        String pem = privateKeyBase64.isBlank() ? normalizeLegacyPem(privateKey) : decodePem(privateKeyBase64);
        PemContents contents = pemContents(pem);
        byte[] der;
        try {
            der = Base64.getDecoder().decode(contents.body().replaceAll("\\s", ""));
        } catch (IllegalArgumentException exception) {
            throw GithubAppClient.configurationMissing();
        }

        byte[] pkcs8 = der;
        if (PKCS1_PEM_TYPE.equals(contents.type())) {
            pkcs8 = asPkcs8(der);
        }

        PrivateKey resolvedKey;
        try {
            resolvedKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (java.security.GeneralSecurityException exception) {
            throw GithubAppClient.configurationMissing();
        }
        if (!(resolvedKey instanceof RSAPrivateKey)) {
            throw GithubAppClient.configurationMissing();
        }
        return resolvedKey;
    }

    private String decodePem(String encodedPem) {
        try {
            return new String(Base64.getDecoder().decode(encodedPem), StandardCharsets.US_ASCII);
        } catch (IllegalArgumentException exception) {
            throw GithubAppClient.configurationMissing();
        }
    }

    private PemContents pemContents(String pem) {
        String normalizedPem = pem.strip();
        PemContents pkcs1 = pemContents(normalizedPem, PKCS1_PEM_TYPE);
        if (pkcs1 != null) return pkcs1;
        PemContents pkcs8 = pemContents(normalizedPem, PKCS8_PEM_TYPE);
        if (pkcs8 != null) return pkcs8;
        throw GithubAppClient.configurationMissing();
    }

    private PemContents pemContents(String pem, String type) {
        String header = "-----BEGIN " + type + "-----";
        String footer = "-----END " + type + "-----";
        if (!pem.startsWith(header) || !pem.endsWith(footer)) return null;
        int footerIndex = pem.lastIndexOf(footer);
        if (footerIndex <= header.length()) return null;
        String body = pem.substring(header.length(), footerIndex);
        return body.isBlank() ? null : new PemContents(type, body);
    }

    private String normalizeLegacyPem(String pem) {
        return pem.replace("\\r\\n", "\n").replace("\\n", "\n");
    }

    private byte[] asPkcs8(byte[] pkcs1) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] privateKeyOctetString = der(0x04, pkcs1);
        byte[] content = new byte[version.length + RSA_ALGORITHM_IDENTIFIER.length + privateKeyOctetString.length];
        System.arraycopy(version, 0, content, 0, version.length);
        System.arraycopy(RSA_ALGORITHM_IDENTIFIER, 0, content, version.length, RSA_ALGORITHM_IDENTIFIER.length);
        System.arraycopy(privateKeyOctetString, 0, content, version.length + RSA_ALGORITHM_IDENTIFIER.length, privateKeyOctetString.length);
        return der(0x30, content);
    }

    private byte[] der(int tag, byte[] value) {
        byte[] length = derLength(value.length);
        byte[] result = new byte[1 + length.length + value.length];
        result[0] = (byte) tag;
        System.arraycopy(length, 0, result, 1, length.length);
        System.arraycopy(value, 0, result, 1 + length.length, value.length);
        return result;
    }

    private byte[] derLength(int length) {
        if (length < 128) return new byte[] {(byte) length};
        int size = Integer.BYTES - Integer.numberOfLeadingZeros(length) / Byte.SIZE;
        byte[] result = new byte[size + 1];
        result[0] = (byte) (0x80 | size);
        for (int index = size; index > 0; index--) {
            result[index] = (byte) length;
            length >>>= Byte.SIZE;
        }
        return result;
    }

    private record PemContents(String type, String body) {}
}
