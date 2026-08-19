package com.no8do.api.credential;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CredentialCryptoService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_VERSION = 1;
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final String encodedMasterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialCryptoService(@Value("${NO8DO_CREDENTIALS_MASTER_KEY:}") String encodedMasterKey) {
        this.encodedMasterKey = encodedMasterKey;
    }

    public EncryptedCredentialSecret encrypt(String secret) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return new EncryptedCredentialSecret(
                Base64.getEncoder().encodeToString(ciphertext),
                Base64.getEncoder().encodeToString(iv),
                KEY_VERSION
            );
        } catch (GeneralSecurityException ex) {
            throw vaultUnavailable();
        }
    }

    public String decrypt(String ciphertext, String iv) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(
                Cipher.DECRYPT_MODE,
                masterKey(),
                new GCMParameterSpec(TAG_LENGTH_BITS, Base64.getDecoder().decode(iv))
            );
            byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            throw vaultUnavailable();
        }
    }

    private SecretKeySpec masterKey() {
        try {
            byte[] key = Base64.getDecoder().decode(encodedMasterKey);
            if (key.length != KEY_LENGTH_BYTES) {
                throw vaultUnavailable();
            }
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException ex) {
            throw vaultUnavailable();
        }
    }

    private ResponseStatusException vaultUnavailable() {
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Credential vault is not configured");
    }
}
