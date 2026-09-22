package com.no8do.api.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.no8do.api.replay.embedding.EmbeddingProvider;
import com.no8do.api.replay.embedding.ReplayVectorSearchHit;
import com.no8do.api.replay.embedding.ReplayVectorSearchService;
import com.no8do.api.workspace.Workspace;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReplayHybridSearchServiceTests {
    private final ReplayService replayService = mock(ReplayService.class);
    private final ReplayVectorSearchService vectorSearch = mock(ReplayVectorSearchService.class);
    private final ReplayHybridSearchService service = new ReplayHybridSearchService(replayService, vectorSearch);

    @Test void propagatesLexicalAuthorizationFailureBeforeVectorSearch() {
        UUID workspaceId = UUID.randomUUID(); UUID currentUserId = UUID.randomUUID(); EmbeddingProvider provider = mock(EmbeddingProvider.class);
        RuntimeException denied = new IllegalStateException("acesso negado");
        when(replayService.search(workspaceId, currentUserId, "auth")).thenThrow(denied);

        assertThatThrownBy(() -> service.search(workspaceId, currentUserId, "auth", provider, 5)).isSameAs(denied);

        verify(replayService).search(workspaceId, currentUserId, "auth");
        verifyNoInteractions(vectorSearch, provider);
    }

    @Test void combinesAuthorizedDiscoveryAndVectorResultsWithControlledRrf() {
        UUID workspaceId = UUID.randomUUID(); UUID currentUserId = UUID.randomUUID(); EmbeddingProvider provider = mock(EmbeddingProvider.class);
        ReplayResponse first = response(UUID.randomUUID(), "lexical first"); ReplayResponse shared = response(UUID.randomUUID(), "shared");
        ReplayVersion vectorFirst = version(UUID.randomUUID()); ReplayVersion vectorShared = version(shared.id());
        when(replayService.search(workspaceId, currentUserId, "query")).thenReturn(List.of(first, shared));
        when(vectorSearch.search(workspaceId, "query", provider, 20)).thenReturn(List.of(new ReplayVectorSearchHit(vectorFirst, 0.01), new ReplayVectorSearchHit(vectorShared, 0.02)));

        List<ReplayHybridSearchHit> result = service.search(workspaceId, currentUserId, "query", provider, 3);

        assertThat(result).extracting(ReplayHybridSearchHit::replayId).containsExactly(shared.id(), first.id(), vectorFirst.getReplay().getId());
        ReplayHybridSearchHit fused = result.getFirst();
        assertThat(fused.lexicalRank()).isEqualTo(2); assertThat(fused.vectorRank()).isEqualTo(2);
        assertThat(fused.hybridScore()).isEqualTo((1D / 62D) + (1D / 62D));
        assertThat(fused.replay()).isSameAs(shared); assertThat(fused.replayVersion()).isSameAs(vectorShared);
    }

    @Test void boundsCandidatePoolDeduplicatesAndUsesDeterministicTieBreaking() {
        UUID workspaceId = UUID.randomUUID(); UUID currentUserId = UUID.randomUUID(); EmbeddingProvider provider = mock(EmbeddingProvider.class);
        UUID lowerId = new UUID(0, 1); UUID higherId = new UUID(0, 2);
        ReplayResponse lower = response(lowerId, "lower"); ReplayResponse higher = response(higherId, "higher");
        when(replayService.search(workspaceId, currentUserId, "query")).thenReturn(List.of(higher, lower));
        when(vectorSearch.search(workspaceId, "query", provider, 100)).thenReturn(List.of());

        List<ReplayHybridSearchHit> result = service.search(workspaceId, currentUserId, "query", provider, 30);

        assertThat(ReplayHybridSearchService.candidateLimit(1)).isEqualTo(20);
        assertThat(ReplayHybridSearchService.candidateLimit(10)).isEqualTo(40);
        assertThat(ReplayHybridSearchService.candidateLimit(30)).isEqualTo(100);
        verify(vectorSearch).search(workspaceId, "query", provider, 100);
        assertThat(result).extracting(ReplayHybridSearchHit::replayId).containsExactly(higherId, lowerId);
    }

    @Test void validatesRequiredInputsBeforeDiscovery() {
        EmbeddingProvider provider = mock(EmbeddingProvider.class);
        assertThatThrownBy(() -> service.search(null, UUID.randomUUID(), "q", provider, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search(UUID.randomUUID(), null, "q", provider, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search(UUID.randomUUID(), UUID.randomUUID(), " ", provider, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.search(UUID.randomUUID(), UUID.randomUUID(), "q", provider, 101)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(replayService, vectorSearch, provider);
    }

    private ReplayResponse response(UUID id, String title) {
        return new ReplayResponse(id, UUID.randomUUID(), null, null, title, ReplayType.PATTERN, null, null, null,
                List.of(), List.of(), ReplayStatus.VALIDATED, 1, 0, 0, 0, null, null, "User", Instant.EPOCH, Instant.EPOCH);
    }

    private ReplayVersion version(UUID replayId) {
        Workspace workspace = new Workspace("Workspace"); Replay replay = new Replay(workspace, null, "Replay", ReplayType.PATTERN, null);
        ReplayVersion version = new ReplayVersion(replay, null);
        ReflectionTestUtils.setField(replay, "id", replayId); ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
        return version;
    }
}
