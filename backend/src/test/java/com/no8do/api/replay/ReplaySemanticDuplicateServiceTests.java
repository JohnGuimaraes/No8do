package com.no8do.api.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.no8do.api.replay.embedding.EmbeddingProvider;
import com.no8do.api.replay.embedding.ReplaySemanticDuplicateQuery;
import com.no8do.api.replay.embedding.ReplayVectorSearchHit;
import com.no8do.api.replay.embedding.ReplayVectorSearchService;
import com.no8do.api.workspace.WorkspaceAuthorizationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReplaySemanticDuplicateServiceTests {
    private final WorkspaceAuthorizationService authorization = mock(WorkspaceAuthorizationService.class);
    private final ReplayVectorSearchService vectorSearch = mock(ReplayVectorSearchService.class);
    private final ReplaySemanticDuplicateService service = new ReplaySemanticDuplicateService(authorization, vectorSearch);

    @Test void canonicalizesCandidateUsesAuthorizedWorkspaceAndFiltersControlledDistances() {
        UUID workspaceId = UUID.randomUUID(); UUID userId = UUID.randomUUID(); UUID self = UUID.randomUUID(); UUID a = UUID.randomUUID(); UUID b = UUID.randomUUID(); UUID c = UUID.randomUUID();
        EmbeddingProvider provider = mock(EmbeddingProvider.class); ReplaySemanticDuplicateQuery query = query();
        List<ReplayVectorSearchHit> hits = List.of(hit(self, 0D), hit(a, .02D), hit(b, .08D), hit(c, .25D));
        when(vectorSearch.search(eq(workspaceId), anyString(), same(provider), eq(6))).thenReturn(hits);

        List<ReplaySemanticDuplicateCandidate> result = service.findDuplicates(workspaceId, userId, query, self, provider, 5, .10D);

        assertThat(result).extracting(ReplaySemanticDuplicateCandidate::replayId).containsExactly(a, b);
        verify(authorization).requireWorkspaceMember(workspaceId, userId);
        verify(vectorSearch).search(eq(workspaceId), contains("Título: Title"), same(provider), eq(6));
    }

    @Test void includesDistanceEqualToThresholdOrdersAndCompensatesSelfWithoutReducingTopK() {
        UUID workspaceId = UUID.randomUUID(); UUID self = UUID.randomUUID(); UUID a = UUID.randomUUID(); UUID b = UUID.randomUUID(); UUID c = UUID.randomUUID();
        EmbeddingProvider provider = mock(EmbeddingProvider.class);
        List<ReplayVectorSearchHit> hits = List.of(hit(self, 0D), hit(b, .10D), hit(a, .02D), hit(c, .11D));
        when(vectorSearch.search(eq(workspaceId), anyString(), same(provider), eq(3))).thenReturn(hits);
        List<ReplaySemanticDuplicateCandidate> result = service.findDuplicates(workspaceId, UUID.randomUUID(), query(), self, provider, 2, .10D);
        assertThat(result).extracting(ReplaySemanticDuplicateCandidate::replayId).containsExactly(a, b);
    }

    @Test void rejectsInvalidLimitsAndThresholdsBeforeAuthorization() {
        UUID workspaceId = UUID.randomUUID(); EmbeddingProvider provider = mock(EmbeddingProvider.class);
        for (double threshold : List.of(-.01D, 2.01D, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThatThrownBy(() -> service.findDuplicates(workspaceId, UUID.randomUUID(), query(), null, provider, 1, threshold)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service.findDuplicates(workspaceId, UUID.randomUUID(), query(), null, provider, 0, .1D)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(authorization, vectorSearch, provider);
    }

    @Test void propagatesProviderFailureAndDeniedAccessPreventsVectorSearch() {
        UUID workspaceId = UUID.randomUUID(); UUID userId = UUID.randomUUID(); EmbeddingProvider provider = mock(EmbeddingProvider.class);
        RuntimeException denied = new IllegalStateException("acesso negado"); doThrow(denied).when(authorization).requireWorkspaceMember(workspaceId, userId);
        assertThatThrownBy(() -> service.findDuplicates(workspaceId, userId, query(), null, provider, 1, .1D)).isSameAs(denied);
        verifyNoInteractions(vectorSearch, provider);

        reset(authorization); RuntimeException unavailable = new IllegalStateException("provider indisponível");
        when(vectorSearch.search(eq(workspaceId), anyString(), same(provider), eq(1))).thenThrow(unavailable);
        assertThatThrownBy(() -> service.findDuplicates(workspaceId, userId, query(), null, provider, 1, .1D)).isSameAs(unavailable);
    }

    @Test void returnsEmptyListWhenNoCandidateMatchesThreshold() {
        UUID workspaceId = UUID.randomUUID(); EmbeddingProvider provider = mock(EmbeddingProvider.class);
        ReplayVectorSearchHit hit = hit(UUID.randomUUID(), .2D);
        when(vectorSearch.search(eq(workspaceId), anyString(), same(provider), eq(1))).thenReturn(List.of(hit));
        assertThat(service.findDuplicates(workspaceId, UUID.randomUUID(), query(), null, provider, 1, .1D)).isEmpty();
    }

    private ReplaySemanticDuplicateQuery query() { return new ReplaySemanticDuplicateQuery("Title", ReplayType.PATTERN, List.of("tag"), List.of("Java"), "problem", "context", "solution"); }
    private ReplayVectorSearchHit hit(UUID id, double distance) { ReplayVersion version = mock(ReplayVersion.class); Replay replay = mock(Replay.class); when(version.getReplay()).thenReturn(replay); when(replay.getId()).thenReturn(id); return new ReplayVectorSearchHit(version, distance); }
}
