package com.no8do.api.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.no8do.api.replay.embedding.EmbeddingProvider;
import com.no8do.api.replay.embedding.ReplayVectorSearchHit;
import com.no8do.api.replay.embedding.ReplayVectorSearchService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReplayRetrievalEvaluationServiceTests {
    private final ReplayService replayService = mock(ReplayService.class);
    private final ReplayVectorSearchService vectorSearch = mock(ReplayVectorSearchService.class);
    private final ReplayHybridSearchService hybridSearch = mock(ReplayHybridSearchService.class);
    private final ReplayRetrievalEvaluationService service = new ReplayRetrievalEvaluationService(replayService, vectorSearch, hybridSearch);

    @Test void evaluatesTheSameGroundTruthAcrossThreeStrategies() {
        UUID workspaceId = UUID.randomUUID(); UUID currentUserId = UUID.randomUUID(); UUID relevant = UUID.randomUUID(); UUID other = UUID.randomUUID();
        EmbeddingProvider provider = mock(EmbeddingProvider.class); ReplayRetrievalEvaluationCase evaluationCase = new ReplayRetrievalEvaluationCase("case", "query", Set.of(relevant));
        ReplayVectorSearchHit vectorHit = vector(relevant);
        when(replayService.search(workspaceId, currentUserId, "query")).thenReturn(List.of(response(other), response(relevant)));
        when(vectorSearch.search(workspaceId, "query", provider, 2)).thenReturn(List.of(vectorHit));
        when(hybridSearch.search(workspaceId, currentUserId, "query", provider, 2)).thenReturn(List.of(new ReplayHybridSearchHit(relevant, response(relevant), null, 1D, 1, null)));

        ReplayRetrievalEvaluationReport report = service.evaluate(workspaceId, currentUserId, List.of(evaluationCase), provider, 2);

        assertThat(report.results()).hasSize(3);
        assertThat(report.results().get(ReplayRetrievalStrategy.LEXICAL).meanMrrAtK()).isEqualTo(.5D);
        assertThat(report.results().get(ReplayRetrievalStrategy.VECTOR).meanPrecisionAtK()).isEqualTo(.5D);
        assertThat(report.results().get(ReplayRetrievalStrategy.HYBRID).meanNdcgAtK()).isEqualTo(1D);
        verify(replayService).search(workspaceId, currentUserId, "query"); verify(vectorSearch).search(workspaceId, "query", provider, 2);
        verify(hybridSearch).search(workspaceId, currentUserId, "query", provider, 2); verifyNoInteractions(provider);
    }

    @Test void propagatesAuthorizationFailureBeforeAnyVectorRetrieval() {
        UUID workspaceId = UUID.randomUUID(); UUID currentUserId = UUID.randomUUID(); EmbeddingProvider provider = mock(EmbeddingProvider.class);
        RuntimeException denied = new IllegalStateException("acesso negado"); when(replayService.search(workspaceId, currentUserId, "query")).thenThrow(denied);
        assertThatThrownBy(() -> service.evaluate(workspaceId, currentUserId, List.of(new ReplayRetrievalEvaluationCase("case", "query", Set.of(UUID.randomUUID()))), provider, 1)).isSameAs(denied);
        verifyNoInteractions(vectorSearch, hybridSearch, provider);
    }

    @Test void propagatesProviderFailureFromVectorSearch() {
        UUID workspaceId = UUID.randomUUID(); UUID currentUserId = UUID.randomUUID(); EmbeddingProvider provider = mock(EmbeddingProvider.class);
        RuntimeException unavailable = new IllegalStateException("provider indisponível"); when(replayService.search(workspaceId, currentUserId, "query")).thenReturn(List.of(response(UUID.randomUUID())));
        when(vectorSearch.search(workspaceId, "query", provider, 1)).thenThrow(unavailable);
        assertThatThrownBy(() -> service.evaluate(workspaceId, currentUserId, List.of(new ReplayRetrievalEvaluationCase("case", "query", Set.of(UUID.randomUUID()))), provider, 1)).isSameAs(unavailable);
        verifyNoInteractions(hybridSearch);
    }

    private ReplayResponse response(UUID id) { return new ReplayResponse(id, UUID.randomUUID(), null, null, "Replay", ReplayType.PATTERN, null, null, null, List.of(), List.of(), ReplayStatus.VALIDATED, 1, 0, 0, 0, null, null, "User", Instant.EPOCH, Instant.EPOCH); }
    private ReplayVectorSearchHit vector(UUID replayId) { ReplayVersion version = mock(ReplayVersion.class); Replay replay = mock(Replay.class); when(version.getReplay()).thenReturn(replay); when(replay.getId()).thenReturn(replayId); return new ReplayVectorSearchHit(version, 0D); }
}
