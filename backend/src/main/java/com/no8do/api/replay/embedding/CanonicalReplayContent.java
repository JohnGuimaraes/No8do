package com.no8do.api.replay.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record CanonicalReplayContent(String text) {

    public CanonicalReplayContent {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Conteúdo canônico é obrigatório.");
        }
    }

    public String contentHash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível.", exception);
        }
    }
}
