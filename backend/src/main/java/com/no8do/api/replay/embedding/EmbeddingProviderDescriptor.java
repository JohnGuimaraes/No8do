package com.no8do.api.replay.embedding;

public record EmbeddingProviderDescriptor(String provider, String model, int dimensions) {

    public EmbeddingProviderDescriptor {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Provider de embedding é obrigatório.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Modelo de embedding é obrigatório.");
        }
        if (dimensions <= 0) {
            throw new IllegalArgumentException("Dimensões de embedding devem ser positivas.");
        }
    }
}
