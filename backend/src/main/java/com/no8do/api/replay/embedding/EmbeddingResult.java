package com.no8do.api.replay.embedding;

public record EmbeddingResult(float[] vector) {

    public EmbeddingResult {
        if (vector == null) {
            throw new IllegalArgumentException("Vetor de embedding é obrigatório.");
        }
        if (vector.length == 0) {
            throw new IllegalArgumentException("Vetor de embedding não pode ser vazio.");
        }
        vector = vector.clone();
    }

    @Override
    public float[] vector() {
        return vector.clone();
    }

    public int dimensions() {
        return vector.length;
    }
}
