package com.no8do.api.replay.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.no8do.api.replay.*;
import com.no8do.api.user.User;
import com.no8do.api.workspace.Workspace;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class ReplayEmbeddingServiceTests {
    private final ReplayEmbeddingRepository repository = mock(ReplayEmbeddingRepository.class);
    private final ReplayEmbeddingPersistence persistence = mock(ReplayEmbeddingPersistence.class);
    private final ReplayEmbeddingService service = new ReplayEmbeddingService(repository, persistence);

    @Test void createsEmbeddingFromTheVersionTuple() {
        ReplayVersion version = version(); EmbeddingProvider provider = provider("fake", "one", 2, new float[] {.1f, .2f});
        CanonicalReplayContent canonical = canonical(version);
        when(repository.findByReplayVersionIdAndProviderAndModelAndDimensions(version.getId(), "fake", "one", 2)).thenReturn(Optional.empty());
        when(persistence.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        ReplayEmbeddingOutcome outcome = service.ensureEmbedding(version, provider);

        ReplayEmbedding saved = outcome.embedding();
        assertThat(outcome.reused()).isFalse();
        assertThat(saved.getWorkspaceId()).isEqualTo(version.getWorkspace().getId());
        assertThat(saved.getReplayId()).isEqualTo(version.getReplay().getId());
        assertThat(saved.getReplayVersionId()).isEqualTo(version.getId());
        assertThat(saved.getProvider()).isEqualTo("fake"); assertThat(saved.getModel()).isEqualTo("one");
        assertThat(saved.getDimensions()).isEqualTo(2); assertThat(saved.getContentHash()).isEqualTo(canonical.contentHash());
        verify(provider, times(1)).embed(canonical.text()); verify(persistence, times(1)).saveAndFlush(saved);
    }

    @Test void reusesExistingMatchingHashWithoutCallingProvider() {
        ReplayVersion version = version(); EmbeddingProvider provider = provider("fake", "one", 2, new float[] {.1f, .2f});
        ReplayEmbedding existing = embedding(version, provider.descriptor(), canonical(version).contentHash(), new float[] {.1f, .2f});
        when(repository.findByReplayVersionIdAndProviderAndModelAndDimensions(version.getId(), "fake", "one", 2)).thenReturn(Optional.of(existing));

        ReplayEmbeddingOutcome outcome = service.ensureEmbedding(version, provider);

        assertThat(outcome.reused()).isTrue(); assertThat(outcome.embedding()).isSameAs(existing); assertThat(existing.getEmbedding()).containsExactly(.1f, .2f);
        verify(provider, never()).embed(any()); verify(persistence, never()).saveAndFlush(any());
    }

    @Test void replacesExistingWhenCanonicalHashDiffers() {
        ReplayVersion version = version(); EmbeddingProvider provider = provider("fake", "one", 2, new float[] {.3f, .4f});
        ReplayEmbedding existing = embedding(version, provider.descriptor(), "0".repeat(64), new float[] {.1f, .2f});
        when(repository.findByReplayVersionIdAndProviderAndModelAndDimensions(version.getId(), "fake", "one", 2)).thenReturn(Optional.of(existing));
        when(persistence.saveAndFlush(existing)).thenReturn(existing);

        ReplayEmbeddingOutcome outcome = service.ensureEmbedding(version, provider);

        assertThat(outcome.reused()).isFalse(); assertThat(outcome.embedding()).isSameAs(existing);
        assertThat(existing.getContentHash()).isEqualTo(canonical(version).contentHash()); assertThat(existing.getEmbedding()).containsExactly(.3f, .4f);
        verify(provider).embed(canonical(version).text()); verify(persistence).saveAndFlush(existing);
    }

    @Test void rejectsDimensionMismatchBeforePersistence() {
        ReplayVersion version = version(); EmbeddingProvider provider = provider("fake", "one", 3, new float[] {.1f, .2f});
        when(repository.findByReplayVersionIdAndProviderAndModelAndDimensions(version.getId(), "fake", "one", 3)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.ensureEmbedding(version, provider)).isInstanceOf(IllegalStateException.class);
        verify(persistence, never()).saveAndFlush(any());
    }

    @Test void propagatesProviderFailureWithoutPersisting() {
        ReplayVersion version = version(); EmbeddingProvider provider = mock(EmbeddingProvider.class); RuntimeException failure = new IllegalStateException("provider indisponível");
        when(provider.descriptor()).thenReturn(new EmbeddingProviderDescriptor("fake", "one", 2)); when(provider.embed(any())).thenThrow(failure);
        when(repository.findByReplayVersionIdAndProviderAndModelAndDimensions(version.getId(), "fake", "one", 2)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.ensureEmbedding(version, provider)).isSameAs(failure);
        verify(persistence, never()).saveAndFlush(any());
    }

    @Test void usesProviderAsPartOfTheLookupKey() { assertLookupKey(provider("provider-a", "model", 2, new float[] {.1f, .2f}), "provider-a", "model", 2); assertLookupKey(provider("provider-b", "model", 2, new float[] {.1f, .2f}), "provider-b", "model", 2); }
    @Test void usesModelAsPartOfTheLookupKey() { assertLookupKey(provider("provider", "model-a", 2, new float[] {.1f, .2f}), "provider", "model-a", 2); assertLookupKey(provider("provider", "model-b", 2, new float[] {.1f, .2f}), "provider", "model-b", 2); }
    @Test void usesDimensionsAsPartOfTheLookupKey() { assertLookupKey(provider("provider", "model", 2, new float[] {.1f, .2f}), "provider", "model", 2); assertLookupKey(provider("provider", "model", 3, new float[] {.1f, .2f, .3f}), "provider", "model", 3); }

    @Test void isSequentiallyIdempotentForTheSameCanonicalContent() {
        ReplayVersion version = version(); EmbeddingProvider provider = provider("fake", "one", 2, new float[] {.1f, .2f}); AtomicReference<ReplayEmbedding> stored = new AtomicReference<>();
        when(repository.findByReplayVersionIdAndProviderAndModelAndDimensions(version.getId(), "fake", "one", 2)).thenAnswer(i -> Optional.ofNullable(stored.get()));
        when(persistence.saveAndFlush(any())).thenAnswer(i -> { ReplayEmbedding value = i.getArgument(0); stored.set(value); return value; });
        ReplayEmbeddingOutcome first = service.ensureEmbedding(version, provider); ReplayEmbeddingOutcome second = service.ensureEmbedding(version, provider);
        assertThat(first.reused()).isFalse(); assertThat(second.reused()).isTrue(); assertThat(second.embedding()).isSameAs(first.embedding());
        verify(provider, times(1)).embed(canonical(version).text()); verify(persistence, times(1)).saveAndFlush(any());
    }

    @Test void reusesConcurrentWinnerWhenItsHashMatches() {
        ReplayVersion version = version(); EmbeddingProvider provider = provider("fake", "one", 2, new float[] {.1f, .2f});
        ReplayEmbedding winner = embedding(version, provider.descriptor(), canonical(version).contentHash(), new float[] {.1f, .2f});
        when(repository.findByReplayVersionIdAndProviderAndModelAndDimensions(version.getId(), "fake", "one", 2)).thenReturn(Optional.empty(), Optional.of(winner));
        when(persistence.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique collision"));

        ReplayEmbeddingOutcome outcome = service.ensureEmbedding(version, provider);

        assertThat(outcome.reused()).isTrue(); assertThat(outcome.embedding()).isSameAs(winner);
        verify(persistence).saveAndFlush(any()); verify(provider, times(1)).embed(canonical(version).text());
    }

    @Test void rejectsConcurrentWinnerWhenItsHashDiffers() {
        ReplayVersion version = version(); EmbeddingProvider provider = provider("fake", "one", 2, new float[] {.1f, .2f});
        ReplayEmbedding winner = embedding(version, provider.descriptor(), "0".repeat(64), new float[] {.1f, .2f});
        DataIntegrityViolationException collision = new DataIntegrityViolationException("unique collision");
        when(repository.findByReplayVersionIdAndProviderAndModelAndDimensions(version.getId(), "fake", "one", 2)).thenReturn(Optional.empty(), Optional.of(winner));
        when(persistence.saveAndFlush(any())).thenThrow(collision);

        assertThatThrownBy(() -> service.ensureEmbedding(version, provider)).isInstanceOf(IllegalStateException.class).hasCause(collision);
    }

    private void assertLookupKey(EmbeddingProvider provider, String expectedProvider, String expectedModel, int expectedDimensions) {
        ReplayVersion version = version(); when(repository.findByReplayVersionIdAndProviderAndModelAndDimensions(version.getId(), expectedProvider, expectedModel, expectedDimensions)).thenReturn(Optional.empty()); when(persistence.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        service.ensureEmbedding(version, provider);
        verify(repository).findByReplayVersionIdAndProviderAndModelAndDimensions(version.getId(), expectedProvider, expectedModel, expectedDimensions);
    }
    private ReplayEmbedding embedding(ReplayVersion version, EmbeddingProviderDescriptor descriptor, String hash, float[] vector) { return new ReplayEmbedding(version.getWorkspace().getId(), version.getReplay().getId(), version.getId(), descriptor, hash, new EmbeddingResult(vector)); }
    private CanonicalReplayContent canonical(ReplayVersion version) { return new ReplayCanonicalizer().canonicalize(version); }
    private EmbeddingProvider provider(String name, String model, int dimensions, float[] vector) { EmbeddingProvider provider = mock(EmbeddingProvider.class); when(provider.descriptor()).thenReturn(new EmbeddingProviderDescriptor(name, model, dimensions)); when(provider.embed(any())).thenReturn(new EmbeddingResult(vector)); return provider; }
    private ReplayVersion version() { Workspace workspace = new Workspace("Workspace"); Replay replay = new Replay(workspace, null, "Title", ReplayType.PATTERN, new User("User", UUID.randomUUID() + "@example.com", "hash")); replay.setTags(new String[] {"auth"}); replay.setStack(new String[] {"Java"}); ReplayVersion version = new ReplayVersion(replay, null); ReflectionTestUtils.setField(workspace, "id", UUID.randomUUID()); ReflectionTestUtils.setField(replay, "id", UUID.randomUUID()); ReflectionTestUtils.setField(version, "id", UUID.randomUUID()); return version; }
}
