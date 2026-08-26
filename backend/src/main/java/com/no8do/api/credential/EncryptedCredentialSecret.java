package com.no8do.api.credential;

public record EncryptedCredentialSecret(String ciphertext, String iv, int keyVersion) {
}
