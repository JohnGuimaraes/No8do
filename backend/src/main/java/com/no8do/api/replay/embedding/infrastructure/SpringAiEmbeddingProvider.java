package com.no8do.api.replay.embedding.infrastructure;

import com.no8do.api.replay.embedding.EmbeddingProvider;
import com.no8do.api.replay.embedding.EmbeddingProviderDescriptor;
import com.no8do.api.replay.embedding.EmbeddingResult;
import org.springframework.ai.embedding.EmbeddingModel;

public final class SpringAiEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProviderDescriptor descriptor;

    public SpringAiEmbeddingProvider(EmbeddingModel embeddingModel, EmbeddingProviderDescriptor descriptor) {
        if (embeddingModel == null) {
            throw new IllegalArgumentException("EmbeddingModel é obrigatório.");
        }
        if (descriptor == null) {
            throw new IllegalArgumentException("Descriptor de embedding é obrigatório.");
        }
        this.embeddingModel = embeddingModel;
        this.descriptor = descriptor;
    }

    @Override
    public EmbeddingProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public EmbeddingResult embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Texto para embedding é obrigatório.");
        }

        EmbeddingResult result = new EmbeddingResult(embeddingModel.embed(text));
        if (result.dimensions() != descriptor.dimensions()) {
            throw new IllegalStateException("Dimensões retornadas pelo modelo não correspondem ao descriptor.");
        }
        return result;
    }
}
