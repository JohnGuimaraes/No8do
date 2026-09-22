package com.no8do.api.replay.embedding;

public interface EmbeddingProvider {

    EmbeddingProviderDescriptor descriptor();

    EmbeddingResult embed(String text);
}
