package com.no8do.api.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReplaySourceExpansionPlannerTests {
    private final ReplaySourceExpansionPlanner planner = new ReplaySourceExpansionPlanner();

    @Test
    void greedilySelectsByRetrievalRankAndContinuesAfterAnOversizedSource() {
        Fixture fixture = fixture(new int[] { 600, 700, 300, 100 });
        ReplaySourceExpansionPolicy policy = new ReplaySourceExpansionPolicy(3, 1000);

        ReplaySourceExpansionResult result = planner.expand(fixture.retrieval(), fixture.budgeted(), policy,
                content -> fixture.costs().get(content));

        assertThat(result.selectedSources()).extracting(entry -> entry.representation().replayId())
                .containsExactly(fixture.ids().get(0), fixture.ids().get(2), fixture.ids().get(3));
        assertThat(result.skippedSources()).singleElement().satisfies(skipped -> {
            assertThat(skipped.representation().replayId()).isEqualTo(fixture.ids().get(1));
            assertThat(skipped.estimatedTokens()).isEqualTo(700);
            assertThat(skipped.reason()).isEqualTo(ReplaySourceSkipReason.TOKEN_BUDGET);
        });
        assertThat(result.usedExpansionTokens()).isEqualTo(1000);
        assertThat(result.remainingExpansionTokens()).isZero();
    }

    @Test
    void onlyExpandsSelectedCompactEntriesAndIgnoresBudgetSkippedEntries() {
        Fixture fixture = fixture(new int[] { 1, 1, 1 });
        ReplayBudgetedContextResult budgeted = budgeted(fixture.compact(), List.of(0, 2), List.of(1));
        AtomicInteger estimates = new AtomicInteger();

        ReplaySourceExpansionResult result = planner.expand(fixture.retrieval(), budgeted,
                new ReplaySourceExpansionPolicy(10, 10), content -> { estimates.incrementAndGet(); return 1; });

        assertThat(result.selectedSources()).extracting(entry -> entry.representation().replayId())
                .containsExactly(fixture.ids().get(0), fixture.ids().get(2));
        assertThat(estimates).hasValue(2);
    }

    @Test
    void retrievalRankDefinesPriorityEvenWhenSelectedCompactsArriveOutOfOrder() {
        Fixture fixture = fixture(new int[] { 1, 1, 1 });
        ReplayBudgetedContextResult reversed = budgeted(fixture.compact(), List.of(2, 1, 0), List.of());

        ReplaySourceExpansionResult result = planner.expand(fixture.retrieval(), reversed,
                new ReplaySourceExpansionPolicy(1, 10), content -> 1);

        assertThat(result.selectedSources()).extracting(entry -> entry.representation().retrievalRank()).containsExactly(1);
        assertThat(result.skippedSources()).extracting(ReplaySkippedFullSource::reason)
                .containsExactly(ReplaySourceSkipReason.SOURCE_LIMIT, ReplaySourceSkipReason.SOURCE_LIMIT);
    }

    @Test
    void respectsSourceLimitAndDoesNotEstimateLaterEntries() {
        Fixture fixture = fixture(new int[] { 100, 100, 100 });
        AtomicInteger estimates = new AtomicInteger();

        ReplaySourceExpansionResult result = planner.expand(fixture.retrieval(), fixture.budgeted(),
                new ReplaySourceExpansionPolicy(2, 1000), content -> { estimates.incrementAndGet(); return 100; });

        assertThat(result.selectedSources()).hasSize(2);
        assertThat(result.skippedSources()).singleElement().satisfies(skipped -> {
            assertThat(skipped.reason()).isEqualTo(ReplaySourceSkipReason.SOURCE_LIMIT);
            assertThat(skipped.estimatedTokens()).isNull();
        });
        assertThat(estimates).hasValue(2);
    }

    @Test
    void preservesGreedyPriorityEvenWhenALaterRankedSourceCostsLess() {
        Fixture fixture = fixture(new int[] { 800, 100 });
        ReplaySourceExpansionResult result = planner.expand(fixture.retrieval(), fixture.budgeted(),
                new ReplaySourceExpansionPolicy(1, 500), content -> fixture.costs().get(content));

        assertThat(result.selectedSources()).extracting(entry -> entry.representation().retrievalRank()).containsExactly(2);
        assertThat(result.skippedSources()).singleElement().satisfies(skipped -> {
            assertThat(skipped.representation().retrievalRank()).isEqualTo(1);
            assertThat(skipped.reason()).isEqualTo(ReplaySourceSkipReason.TOKEN_BUDGET);
        });
    }

    @Test
    void buildsUntruncatedSemanticOnlyFullSourceAndPreservesProvenance() {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String longText = "problem-".repeat(100);
        ReplayResponse response = new ReplayResponse(id, workspaceId, null, null, " Título ", ReplayType.PATTERN,
                longText, "solution", "context", List.of("tag"), List.of("Java"), ReplayStatus.VALIDATED,
                9, 500, 400, 100, Instant.parse("2026-01-01T00:00:00Z"), userId, "Sensitive User",
                Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        ReplayRetrievalCandidate candidate = candidate(id, response, null, 0.75, 2, 4, 7);
        ReplayCompactRepresentation compact = new ReplayCompactRepresentation(id, 7, 0.75, 2, 4,
                "Title: Título\nType: PATTERN", true);
        ReplayBudgetedContextResult budgeted = new ReplayBudgetedContextResult(new ReplayContextBudget(20, 0),
                List.of(new ReplayBudgetedContextEntry(compact, 10)), List.of(), 10, 10);
        ReplayRetrievalResult retrieval = retrieval(List.of(candidate));

        ReplayFullSourceRepresentation full = planner.expand(retrieval, budgeted,
                new ReplaySourceExpansionPolicy(1, 100), text -> 50).selectedSources().getFirst().representation();

        assertThat(full.content()).contains("Problem: " + longText).doesNotContain("Sensitive User", workspaceId.toString(),
                userId.toString(), "VALIDATED", "2025-", "2026-");
        assertThat(full).extracting(ReplayFullSourceRepresentation::replayId,
                ReplayFullSourceRepresentation::retrievalRank, ReplayFullSourceRepresentation::hybridScore,
                ReplayFullSourceRepresentation::lexicalRank, ReplayFullSourceRepresentation::vectorRank)
                .containsExactly(id, 7, 0.75, 2, 4);
    }

    @Test
    void prefersCurrentVersionAndFallsBackToResponse() {
        UUID snapshotId = UUID.randomUUID();
        ReplayRetrievalCandidate withBoth = candidate(snapshotId,
                response(snapshotId, "Old response", "old problem"), version(snapshotId, "Current snapshot", "new problem"),
                0.7, 1, 2, 1);
        UUID fallbackId = UUID.randomUUID();
        ReplayRetrievalCandidate responseOnly = candidate(fallbackId,
                response(fallbackId, "fallback", "fallback problem"), null, 0.4, 1, null, 2);

        assertThat(new ReplayFullSourceFactory().full(withBoth).content()).contains("Current snapshot", "new problem")
                .doesNotContain("Old response", "old problem");
        assertThat(new ReplayFullSourceFactory().full(responseOnly).content()).contains("fallback", "fallback problem");
    }

    @Test
    void rejectsDivergentIdentitiesAndUnmatchedSelectedEntries() {
        UUID id = UUID.randomUUID();
        ReplayRetrievalCandidate wrongResponse = candidate(id, response(UUID.randomUUID(), "wrong", "p"), null, 0.5, 1, null, 1);
        assertThatThrownBy(() -> new ReplayFullSourceFactory().full(wrongResponse))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Identidade");

        ReplayCompactRepresentation orphan = new ReplayCompactRepresentation(UUID.randomUUID(), 1, 0.5, 1, null, "Title: orphan", false);
        ReplayBudgetedContextResult budgeted = new ReplayBudgetedContextResult(new ReplayContextBudget(10, 0),
                List.of(new ReplayBudgetedContextEntry(orphan, 1)), List.of(), 1, 9);
        assertThatThrownBy(() -> planner.expand(retrieval(List.of()), budgeted,
                new ReplaySourceExpansionPolicy(1, 10), text -> 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("correspondente");
    }

    @Test
    void doesNotEstimateWhenThereAreNoSelectedCompactsAndReturnsFullRemainingBudget() {
        ReplayBudgetedContextResult empty = new ReplayBudgetedContextResult(new ReplayContextBudget(10, 0),
                List.of(), List.of(), 0, 10);
        TextTokenEstimator estimator = mock(TextTokenEstimator.class);

        ReplaySourceExpansionResult result = planner.expand(retrieval(List.of()), empty,
                new ReplaySourceExpansionPolicy(3, 250), estimator);

        assertThat(result.selectedSources()).isEmpty();
        assertThat(result.skippedSources()).isEmpty();
        assertThat(result.usedExpansionTokens()).isZero();
        assertThat(result.remainingExpansionTokens()).isEqualTo(250);
        verifyNoInteractions(estimator);
    }

    @Test
    void callsEstimatorOncePerEvaluatedSourceAndPropagatesFailure() {
        Fixture fixture = fixture(new int[] { 1, 1 });
        AtomicInteger estimates = new AtomicInteger();
        planner.expand(fixture.retrieval(), fixture.budgeted(), new ReplaySourceExpansionPolicy(2, 10),
                text -> { estimates.incrementAndGet(); return 1; });
        assertThat(estimates).hasValue(2);

        assertThatThrownBy(() -> planner.expand(fixture.retrieval(), fixture.budgeted(),
                new ReplaySourceExpansionPolicy(2, 10), text -> { throw new IllegalStateException("estimator failed"); }))
                .isInstanceOf(IllegalStateException.class).hasMessage("estimator failed");
        assertThatThrownBy(() -> planner.expand(fixture.retrieval(), fixture.budgeted(),
                new ReplaySourceExpansionPolicy(2, 10), text -> 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maior que zero");
    }

    @Test
    void returnsImmutableDeterministicListsAndValidatesPolicyAndEntries() {
        Fixture fixture = fixture(new int[] { 1, 2 });
        ReplaySourceExpansionResult first = planner.expand(fixture.retrieval(), fixture.budgeted(),
                new ReplaySourceExpansionPolicy(10, 1), text -> fixture.costs().get(text));
        ReplaySourceExpansionResult second = planner.expand(fixture.retrieval(), fixture.budgeted(),
                new ReplaySourceExpansionPolicy(10, 1), text -> fixture.costs().get(text));
        assertThat(first).isEqualTo(second);
        assertThatThrownBy(() -> first.selectedSources().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.skippedSources().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new ReplaySourceExpansionPolicy(0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplaySourceExpansionPolicy(11, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplaySourceExpansionPolicy(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayFullSourceEntry(new ReplayFullSourceFactory().full(fixture.retrieval().candidates().getFirst()), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void integratesBudgetedContextIntoSourceExpansionWithoutRepeatingRetrieval() {
        Fixture fixture = fixture(new int[] { 2, 2, 2 });
        ReplayBudgetedContextResult budgeted = budgeted(fixture.compact(), List.of(0, 2), List.of(1));

        ReplaySourceExpansionResult result = planner.expand(fixture.retrieval(), budgeted,
                new ReplaySourceExpansionPolicy(2, 4), text -> 2);

        assertThat(result.selectedSources()).extracting(entry -> entry.representation().retrievalRank()).containsExactly(1, 3);
        assertThat(result.usedExpansionTokens()).isEqualTo(4);
        assertThat(result.remainingExpansionTokens()).isZero();
    }

    private Fixture fixture(int[] costs) {
        List<UUID> ids = java.util.stream.IntStream.range(0, costs.length).mapToObj(index -> UUID.randomUUID()).toList();
        List<ReplayRetrievalCandidate> candidates = new java.util.ArrayList<>();
        List<ReplayCompactRepresentation> compact = new java.util.ArrayList<>();
        java.util.Map<String, Integer> byContent = new java.util.HashMap<>();
        for (int index = 0; index < costs.length; index++) {
            UUID id = ids.get(index);
            ReplayRetrievalCandidate candidate = candidate(id, response(id, "Source " + index, "Problem " + index), null,
                    1.0 - index / 10.0, index + 1, null, index + 1);
            candidates.add(candidate);
            compact.add(new ReplayCompactRepresentation(id, index + 1, candidate.hybridScore(), index + 1, null,
                    "compact " + index, false));
            byContent.put(new ReplayFullSourceFactory().full(candidate).content(), costs[index]);
        }
        return new Fixture(retrieval(candidates), budgeted(compact, java.util.stream.IntStream.range(0, costs.length).boxed().toList(), List.of()), ids, byContent, compact);
    }

    private ReplayBudgetedContextResult budgeted(List<ReplayCompactRepresentation> compact, List<Integer> selected, List<Integer> skipped) {
        List<ReplayBudgetedContextEntry> selectedEntries = selected.stream().map(index -> new ReplayBudgetedContextEntry(compact.get(index), 1)).toList();
        List<ReplayBudgetedContextEntry> skippedEntries = skipped.stream().map(index -> new ReplayBudgetedContextEntry(compact.get(index), 1)).toList();
        return new ReplayBudgetedContextResult(new ReplayContextBudget(Math.max(1, selectedEntries.size() + skippedEntries.size()), 0),
                selectedEntries, skippedEntries, selectedEntries.size(), skippedEntries.size());
    }

    private ReplayRetrievalResult retrieval(List<ReplayRetrievalCandidate> candidates) {
        return new ReplayRetrievalResult(UUID.randomUUID(), "query", Math.max(1, candidates.size()), candidates.size(), candidates);
    }

    private ReplayRetrievalCandidate candidate(UUID id, ReplayResponse response, ReplayVersion version,
            double score, Integer lexicalRank, Integer vectorRank, int retrievalRank) {
        return new ReplayRetrievalCandidate(id, response, version, score, lexicalRank, vectorRank, retrievalRank);
    }

    private ReplayResponse response(UUID id, String title, String problem) {
        return new ReplayResponse(id, UUID.randomUUID(), null, null, title, ReplayType.PATTERN, problem, null, null,
                List.of(), List.of(), ReplayStatus.DRAFT, 1, 0, 0, 0, null, null, null, Instant.EPOCH, Instant.EPOCH);
    }

    private ReplayVersion version(UUID id, String title, String problem) {
        Replay replay = mock(Replay.class);
        when(replay.getId()).thenReturn(id);
        ReplayVersion version = mock(ReplayVersion.class);
        when(version.getReplay()).thenReturn(replay);
        when(version.getTitle()).thenReturn(title);
        when(version.getType()).thenReturn(ReplayType.PATTERN);
        when(version.getTags()).thenReturn(new String[] { "snapshot-tag" });
        when(version.getStack()).thenReturn(new String[] { "snapshot-stack" });
        when(version.getProblem()).thenReturn(problem);
        when(version.getContext()).thenReturn("snapshot-context");
        when(version.getSolution()).thenReturn("snapshot-solution");
        return version;
    }

    private record Fixture(ReplayRetrievalResult retrieval, ReplayBudgetedContextResult budgeted, List<UUID> ids,
            java.util.Map<String, Integer> costs, List<ReplayCompactRepresentation> compact) { }
}
