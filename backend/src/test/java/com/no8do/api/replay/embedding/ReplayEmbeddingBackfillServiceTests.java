package com.no8do.api.replay.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.no8do.api.replay.*;
import com.no8do.api.user.User;
import com.no8do.api.workspace.Workspace;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class ReplayEmbeddingBackfillServiceTests {
    private final ReplayVersionRepository versions = mock(ReplayVersionRepository.class);
    private final ReplayEmbeddingService embeddings = mock(ReplayEmbeddingService.class);
    private final ReplayEmbeddingBackfillService service = new ReplayEmbeddingBackfillService(versions, embeddings);
    private final EmbeddingProvider provider = mock(EmbeddingProvider.class);

    @Test
    void processesOnlyTheRequestedWorkspaceCurrentEligibleVersionsAcrossBatches() {
        UUID workspaceId = UUID.randomUUID();
        ReplayVersion draft = version(workspaceId, ReplayStatus.DRAFT, 1);
        ReplayVersion validated = version(workspaceId, ReplayStatus.VALIDATED, 2);
        ReplayVersion deprecated = version(workspaceId, ReplayStatus.DEPRECATED, 3);
        when(versions.findEligibleCurrentByWorkspaceId(eq(workspaceId), anyList(), eq(PageRequest.of(0, 2))))
                .thenReturn(new PageImpl<>(List.of(draft, validated), PageRequest.of(0, 2), 3));
        when(versions.findEligibleCurrentByWorkspaceId(eq(workspaceId), anyList(), eq(PageRequest.of(1, 2))))
                .thenReturn(new PageImpl<>(List.of(deprecated), PageRequest.of(1, 2), 3));
        when(embeddings.ensureEmbedding(draft, provider)).thenReturn(new ReplayEmbeddingOutcome(null, false));
        when(embeddings.ensureEmbedding(validated, provider)).thenReturn(new ReplayEmbeddingOutcome(null, true));
        when(embeddings.ensureEmbedding(deprecated, provider)).thenReturn(new ReplayEmbeddingOutcome(null, false));

        ReplayEmbeddingBackfillResult result = service.backfill(workspaceId, provider, 2);

        assertThat(result).isEqualTo(new ReplayEmbeddingBackfillResult(3, 3, 2, 1));
        verify(versions).findEligibleCurrentByWorkspaceId(eq(workspaceId), anyList(), eq(PageRequest.of(0, 2)));
        verify(versions).findEligibleCurrentByWorkspaceId(eq(workspaceId), anyList(), eq(PageRequest.of(1, 2)));
        verify(embeddings).ensureEmbedding(draft, provider);
        verify(embeddings).ensureEmbedding(validated, provider);
        verify(embeddings).ensureEmbedding(deprecated, provider);
        verifyNoMoreInteractions(embeddings);
        verifyNoInteractions(provider);
    }

    @Test
    void returnsZeroesWhenTheWorkspaceHasNoEligibleCurrentVersion() {
        UUID workspaceId = UUID.randomUUID();
        when(versions.findEligibleCurrentByWorkspaceId(eq(workspaceId), anyList(), eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        assertThat(service.backfill(workspaceId, provider, 10))
                .isEqualTo(new ReplayEmbeddingBackfillResult(0, 0, 0, 0));
        verifyNoInteractions(embeddings, provider);
    }

    @Test
    void propagatesEmbeddingFailuresWithoutCountingTheFailedVersion() {
        UUID workspaceId = UUID.randomUUID();
        ReplayVersion version = version(workspaceId, ReplayStatus.DRAFT, 1);
        RuntimeException failure = new IllegalStateException("provider indisponível");
        when(versions.findEligibleCurrentByWorkspaceId(eq(workspaceId), anyList(), eq(PageRequest.of(0, 1))))
                .thenReturn(new PageImpl<>(List.of(version), PageRequest.of(0, 1), 1));
        when(embeddings.ensureEmbedding(version, provider)).thenThrow(failure);

        assertThatThrownBy(() -> service.backfill(workspaceId, provider, 1)).isSameAs(failure);
        verify(embeddings).ensureEmbedding(version, provider);
    }

    @Test
    void rejectsANonPositiveBatchSize() {
        assertThatThrownBy(() -> service.backfill(UUID.randomUUID(), provider, 0))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(versions, embeddings, provider);
    }

    private ReplayVersion version(UUID workspaceId, ReplayStatus status, int versionNumber) {
        Workspace workspace = new Workspace("Workspace");
        Replay replay = new Replay(workspace, null, "Title", ReplayType.PATTERN,
                new User("User", UUID.randomUUID() + "@example.com", "hash"));
        replay.setStatus(status);
        ReplayVersion version = new ReplayVersion(replay, null);
        ReflectionTestUtils.setField(workspace, "id", workspaceId);
        ReflectionTestUtils.setField(replay, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(replay, "version", versionNumber);
        ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(version, "version", versionNumber);
        return version;
    }
}
