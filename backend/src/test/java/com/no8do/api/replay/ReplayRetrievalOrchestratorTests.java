package com.no8do.api.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.no8do.api.replay.embedding.EmbeddingProvider;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReplayRetrievalOrchestratorTests {
    private final ReplayHybridSearchService hybridSearch = mock(ReplayHybridSearchService.class);
    private final ReplayRetrievalOrchestrator orchestrator = new ReplayRetrievalOrchestrator(hybridSearch);

    @Test void preservesHybridOrderScoresAndProvenanceWithExplicitRetrievalRanks() {
        UUID workspaceId = UUID.randomUUID(); UUID userId = UUID.randomUUID(); EmbeddingProvider provider = mock(EmbeddingProvider.class);
        ReplayHybridSearchHit a = hit(UUID.randomUUID(), .50D, 1, 3); ReplayHybridSearchHit b = hit(UUID.randomUUID(), .40D, null, 1); ReplayHybridSearchHit c = hit(UUID.randomUUID(), .30D, 2, null);
        when(hybridSearch.search(workspaceId, userId, "query", provider, 3)).thenReturn(List.of(a, b, c));

        ReplayRetrievalResult result = orchestrator.retrieve(workspaceId, userId, "query", provider, 3);

        assertThat(result.workspaceId()).isEqualTo(workspaceId); assertThat(result.query()).isEqualTo("query"); assertThat(result.candidateLimit()).isEqualTo(3); assertThat(result.returnedCandidates()).isEqualTo(3);
        assertThat(result.candidates()).extracting(ReplayRetrievalCandidate::replayId).containsExactly(a.replayId(), b.replayId(), c.replayId());
        assertThat(result.candidates()).extracting(ReplayRetrievalCandidate::retrievalRank).containsExactly(1, 2, 3);
        assertThat(result.candidates()).extracting(ReplayRetrievalCandidate::hybridScore).containsExactly(.50D, .40D, .30D);
        assertThat(result.candidates()).extracting(ReplayRetrievalCandidate::lexicalRank).containsExactly(1, null, 2);
        assertThat(result.candidates()).extracting(ReplayRetrievalCandidate::vectorRank).containsExactly(3, 1, null);
        verify(hybridSearch).search(workspaceId, userId, "query", provider, 3); verifyNoInteractions(provider);
    }

    @Test void returnsImmutableEmptyResult() {
        UUID workspaceId = UUID.randomUUID(); UUID userId = UUID.randomUUID(); EmbeddingProvider provider = mock(EmbeddingProvider.class);
        when(hybridSearch.search(workspaceId, userId, "query", provider, 5)).thenReturn(List.of());
        ReplayRetrievalResult result = orchestrator.retrieve(workspaceId, userId, "query", provider, 5);
        assertThat(result.returnedCandidates()).isZero(); assertThat(result.candidates()).isEmpty();
        assertThatThrownBy(() -> result.candidates().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void validatesInputsWithoutCallingHybridOrProvider() {
        EmbeddingProvider provider = mock(EmbeddingProvider.class);
        assertThatThrownBy(() -> orchestrator.retrieve(null, UUID.randomUUID(), "q", provider, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orchestrator.retrieve(UUID.randomUUID(), null, "q", provider, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orchestrator.retrieve(UUID.randomUUID(), UUID.randomUUID(), " ", provider, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orchestrator.retrieve(UUID.randomUUID(), UUID.randomUUID(), "q", null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orchestrator.retrieve(UUID.randomUUID(), UUID.randomUUID(), "q", provider, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orchestrator.retrieve(UUID.randomUUID(), UUID.randomUUID(), "q", provider, 101)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(hybridSearch, provider);
    }

    @Test void propagatesHybridAuthorizationFailureWithoutFallbackOrProviderCall() {
        UUID workspaceId = UUID.randomUUID(); UUID userId = UUID.randomUUID(); EmbeddingProvider provider = mock(EmbeddingProvider.class); RuntimeException denied = new IllegalStateException("acesso negado");
        when(hybridSearch.search(workspaceId, userId, "query", provider, 1)).thenThrow(denied);
        assertThatThrownBy(() -> orchestrator.retrieve(workspaceId, userId, "query", provider, 1)).isSameAs(denied);
        verify(hybridSearch).search(workspaceId, userId, "query", provider, 1); verifyNoInteractions(provider); verifyNoMoreInteractions(hybridSearch);
    }

    @Test void rejectsCandidateWithoutRetrievalProvenance() {
        UUID workspaceId = UUID.randomUUID(); UUID userId = UUID.randomUUID(); EmbeddingProvider provider = mock(EmbeddingProvider.class);
        when(hybridSearch.search(workspaceId, userId, "query", provider, 1)).thenReturn(List.of(new ReplayHybridSearchHit(UUID.randomUUID(), null, null, .1D, null, null)));
        assertThatThrownBy(() -> orchestrator.retrieve(workspaceId, userId, "query", provider, 1)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("origem");
    }

    private ReplayHybridSearchHit hit(UUID id, double score, Integer lexicalRank, Integer vectorRank) { return new ReplayHybridSearchHit(id, null, null, score, lexicalRank, vectorRank); }
}
