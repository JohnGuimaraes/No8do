package com.no8do.api.credential;

record EncryptedCredentialSecret(String ciphertext, String iv, int keyVersion) {
}
