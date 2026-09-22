package com.no8do.api.replay.embedding.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.no8do.api.replay.embedding.EmbeddingProviderDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

class SpringAiEmbeddingProviderTests {

    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final SpringAiEmbeddingProvider provider = new SpringAiEmbeddingProvider(
            embeddingModel, new EmbeddingProviderDescriptor("test-provider", "test-model", 3));

    @Test
    void sendsOriginalTextAndConvertsEmbedding() {
        when(embeddingModel.embed("Conteúdo técnico")).thenReturn(new float[] { 0.1f, 0.2f, 0.3f });

        var result = provider.embed("Conteúdo técnico");

        verify(embeddingModel).embed("Conteúdo técnico");
        assertThat(result.vector()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(result.dimensions()).isEqualTo(3);
    }

    @Test
    void rejectsInvalidTextBeforeCallingModel() {
        assertThatThrownBy(() -> provider.embed(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.embed("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.embed("   ")).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(embeddingModel);
    }

    @Test
    void propagatesModelFailure() {
        IllegalStateException failure = new IllegalStateException("provider indisponível");
        when(embeddingModel.embed("conteúdo")).thenThrow(failure);

        assertThatThrownBy(() -> provider.embed("conteúdo")).isSameAs(failure);
    }
}
