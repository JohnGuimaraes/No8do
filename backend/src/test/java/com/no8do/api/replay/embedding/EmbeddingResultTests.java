package com.no8do.api.replay.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmbeddingResultTests {

    @Test
    void rejectsNullVector() {
        assertThatThrownBy(() -> new EmbeddingResult(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyVector() {
        assertThatThrownBy(() -> new EmbeddingResult(new float[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsDimensionsFromVectorLength() {
        assertThat(new EmbeddingResult(new float[] { 0.1f, 0.2f, 0.3f }).dimensions()).isEqualTo(3);
    }

    @Test
    void protectsVectorFromExternalMutation() {
        float[] vector = new float[] { 0.1f, 0.2f };
        EmbeddingResult result = new EmbeddingResult(vector);

        vector[0] = 9.9f;
        float[] exposedVector = result.vector();
        exposedVector[1] = 8.8f;

        assertThat(result.vector()).containsExactly(0.1f, 0.2f);
    }
}
