package com.no8do.api.replay.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.no8do.api.replay.ReplayVersion;
import com.no8do.api.replay.ReplayVersionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReplayVectorSearchServiceTests {
    private final ReplayEmbeddingRepository embeddings = mock(ReplayEmbeddingRepository.class);
    private final ReplayVersionRepository versions = mock(ReplayVersionRepository.class);
    private final ReplayVectorSearchService service = new ReplayVectorSearchService(embeddings, versions);

    @Test void validatesInputAndProviderResultBeforeQuery() {
        EmbeddingProvider provider = mock(EmbeddingProvider.class);
        assertThatThrownBy(() -> service.search(UUID.randomUUID(), " ", provider, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search(UUID.randomUUID(), "q", provider, 0)).isInstanceOf(IllegalArgumentException.class);
        when(provider.descriptor()).thenReturn(new EmbeddingProviderDescriptor("p", "m", 2)); when(provider.embed("q")).thenReturn(new EmbeddingResult(new float[] {1}));
        assertThatThrownBy(() -> service.search(UUID.randomUUID(), "q", provider, 1)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(embeddings, versions);
    }

    @Test void rejectsTopKAboveTheDefensiveMaximumAndPropagatesProviderFailure() {
        EmbeddingProvider provider = mock(EmbeddingProvider.class);
        assertThatThrownBy(() -> service.search(UUID.randomUUID(), "q", provider, 101)).isInstanceOf(IllegalArgumentException.class);
        RuntimeException failure = new IllegalStateException("provider indisponível"); when(provider.descriptor()).thenReturn(new EmbeddingProviderDescriptor("p", "m", 2)); when(provider.embed("q")).thenThrow(failure);
        assertThatThrownBy(() -> service.search(UUID.randomUUID(), "q", provider, 1)).isSameAs(failure);
        verifyNoInteractions(embeddings, versions);
    }

    @Test void embedsOnceAndDelegatesCompatibleWorkspaceScopedTopKQuery() {
        UUID workspaceId = UUID.randomUUID(); UUID versionId = UUID.randomUUID(); EmbeddingProvider provider = mock(EmbeddingProvider.class); ReplayVectorSearchRow row = mock(ReplayVectorSearchRow.class); ReplayVersion version = mock(ReplayVersion.class);
        when(provider.descriptor()).thenReturn(new EmbeddingProviderDescriptor("p", "m", 2)); when(provider.embed("semantic query")).thenReturn(new EmbeddingResult(new float[] {1, 0})); when(row.getReplayVersionId()).thenReturn(versionId); when(row.getDistance()).thenReturn(0d);
        when(embeddings.searchCurrentByVector(workspaceId, "p", "m", 2, "[1.0,0.0]", 3)).thenReturn(List.of(row)); when(versions.findAllById(List.of(versionId))).thenReturn(List.of(version)); when(version.getId()).thenReturn(versionId);
        assertThat(service.search(workspaceId, "semantic query", provider, 3)).containsExactly(new ReplayVectorSearchHit(version, 0d));
        verify(provider, times(1)).descriptor(); verify(provider, times(1)).embed("semantic query"); verifyNoMoreInteractions(provider);
    }
}
