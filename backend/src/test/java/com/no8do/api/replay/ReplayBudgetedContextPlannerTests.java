package com.no8do.api.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReplayBudgetedContextPlannerTests {
    private final ReplayBudgetedContextPlanner planner = new ReplayBudgetedContextPlanner();
    private final ReplayCompactRepresentationPolicy compactPolicy = new ReplayCompactRepresentationPolicy(4, 2, 2, 20, 20, 20);
    private final ReplayContextBudget budget = new ReplayContextBudget(140, 40);

    @Test
    void estimatesEachCompactContentOnceAndUsesTheRealAllocatorSelectionAndCosts() {
        List<ReplayRetrievalCandidate> candidates = List.of(
                candidate("Alpha", 1, 0.91, 1, null), candidate("Beta", 2, 0.82, 2, 1),
                candidate("Charlie", 3, 0.73, null, 2), candidate("Delta", 4, 0.64, 4, 3));
        ReplayRetrievalResult retrieval = retrieval(candidates);
        Map<String, Integer> costs = Map.of(
                "Title: Alp…\nType: PATTERN", 50,
                "Title: Beta\nType: PATTERN", 60,
                "Title: Cha…\nType: PATTERN", 30,
                "Title: Del…\nType: PATTERN", 20);
        List<String> estimatedContents = new ArrayList<>();

        ReplayBudgetedContextResult result = planner.plan(retrieval, compactPolicy, budget, content -> {
            estimatedContents.add(content);
            Integer cost = costs.get(content);
            if (cost == null) throw new AssertionError("Conteúdo compacto sem custo de teste: " + content);
            return cost;
        });

        assertThat(estimatedContents).containsExactly(
                "Title: Alp…\nType: PATTERN", "Title: Beta\nType: PATTERN",
                "Title: Cha…\nType: PATTERN", "Title: Del…\nType: PATTERN");
        assertThat(result.budget()).isEqualTo(budget);
        assertThat(result.selectedEntries()).extracting(entry -> entry.representation().replayId())
                .containsExactly(candidates.get(0).replayId(), candidates.get(2).replayId(), candidates.get(3).replayId());
        assertThat(result.skippedEntries()).extracting(entry -> entry.representation().replayId())
                .containsExactly(candidates.get(1).replayId());
        assertThat(result.selectedEntries()).extracting(ReplayBudgetedContextEntry::estimatedTokens).containsExactly(50, 30, 20);
        assertThat(result.skippedEntries()).extracting(ReplayBudgetedContextEntry::estimatedTokens).containsExactly(60);
        assertThat(result.usedTokens()).isEqualTo(100);
        assertThat(result.remainingTokens()).isZero();
    }

    @Test
    void preservesRepresentationProvenanceInsideBudgetEntry() {
        ReplayRetrievalCandidate candidate = candidate("Alpha", 7, 0.73125, 3, 2);
        ReplayBudgetedContextResult result = planner.plan(retrieval(List.of(candidate)), compactPolicy,
                new ReplayContextBudget(10, 0), content -> 1);

        ReplayCompactRepresentation representation = result.selectedEntries().getFirst().representation();
        assertThat(representation.replayId()).isEqualTo(candidate.replayId());
        assertThat(representation.retrievalRank()).isEqualTo(7);
        assertThat(representation.hybridScore()).isEqualTo(0.73125);
        assertThat(representation.lexicalRank()).isEqualTo(3);
        assertThat(representation.vectorRank()).isEqualTo(2);
        assertThat(representation.truncated()).isTrue();
        assertThat(result.selectedEntries().getFirst().estimatedTokens()).isOne();
    }

    @Test
    void preservesAllocatorOrderForMultipleSkippedEntries() {
        ReplayRetrievalResult retrieval = retrieval(List.of(
                candidate("Alpha", 1, 0.9, 1, null), candidate("Beta", 2, 0.8, 2, null),
                candidate("Charlie", 3, 0.7, 3, null), candidate("Delta", 4, 0.6, 4, null)));
        Map<String, Integer> costs = Map.of(
                "Title: Alp…\nType: PATTERN", 31,
                "Title: Beta\nType: PATTERN", 20,
                "Title: Cha…\nType: PATTERN", 15,
                "Title: Del…\nType: PATTERN", 10);

        ReplayBudgetedContextResult result = planner.plan(retrieval, compactPolicy,
                new ReplayContextBudget(30, 0), costs::get);

        assertThat(result.selectedEntries()).extracting(entry -> entry.representation().retrievalRank()).containsExactly(2, 4);
        assertThat(result.skippedEntries()).extracting(entry -> entry.representation().retrievalRank()).containsExactly(1, 3);
    }

    @Test
    void zeroCandidatesReturnEmptyAllocationWithoutEstimating() {
        ReplayBudgetedContextResult result = planner.plan(retrieval(List.of()), compactPolicy, budget,
                content -> { throw new AssertionError("estimator não deve ser chamado"); });
        assertThat(result.selectedEntries()).isEmpty();
        assertThat(result.skippedEntries()).isEmpty();
        assertThat(result.usedTokens()).isZero();
        assertThat(result.remainingTokens()).isEqualTo(budget.availableTokens());
    }

    @Test
    void invalidEstimatesFailWithoutFallbackOrPartialResult() {
        ReplayRetrievalResult retrieval = retrieval(List.of(candidate("Alpha", 1, 0.5, 1, null)));
        for (int estimate : List.of(0, -1)) {
            assertThatThrownBy(() -> planner.plan(retrieval, compactPolicy, budget, content -> estimate))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maior que zero");
        }
    }

    @Test
    void propagatesEstimatorException() {
        ReplayRetrievalResult retrieval = retrieval(List.of(candidate("Alpha", 1, 0.5, 1, null)));
        IllegalStateException failure = new IllegalStateException("estimator indisponível");
        assertThatThrownBy(() -> planner.plan(retrieval, compactPolicy, budget, content -> { throw failure; }))
                .isSameAs(failure);
    }

    @Test
    void rejectsNullInputs() {
        ReplayRetrievalResult retrieval = retrieval(List.of(candidate("Alpha", 1, 0.5, 1, null)));
        assertThatThrownBy(() -> planner.plan(null, compactPolicy, budget, content -> 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planner.plan(retrieval, null, budget, content -> 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planner.plan(retrieval, compactPolicy, null, content -> 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planner.plan(retrieval, compactPolicy, budget, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsImmutableSelectedAndSkippedEntriesAndIsDeterministic() {
        ReplayRetrievalResult retrieval = retrieval(List.of(
                candidate("Alpha", 1, 0.9, 1, null), candidate("Beta", 2, 0.8, 2, null)));
        TextTokenEstimator estimator = content -> 1;
        ReplayBudgetedContextResult first = planner.plan(retrieval, compactPolicy, budget, estimator);
        ReplayBudgetedContextResult second = planner.plan(retrieval, compactPolicy, budget, estimator);

        assertThat(first).isEqualTo(second);
        assertThatThrownBy(() -> first.selectedEntries().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.skippedEntries().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void entryValidatesRepresentationAndPositiveEstimatedCost() {
        ReplayCompactRepresentation representation = new ReplayCompactRepresentation(UUID.randomUUID(), 1, 0.5, 1, null, "Title: x", false);
        assertThatThrownBy(() -> new ReplayBudgetedContextEntry(null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayBudgetedContextEntry(representation, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayBudgetedContextEntry(representation, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    private ReplayRetrievalResult retrieval(List<ReplayRetrievalCandidate> candidates) {
        return new ReplayRetrievalResult(UUID.randomUUID(), "query", 100, candidates.size(), candidates);
    }

    private ReplayRetrievalCandidate candidate(String title, int rank, double score, Integer lexicalRank, Integer vectorRank) {
        UUID id = UUID.nameUUIDFromBytes(title.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ReplayResponse response = new ReplayResponse(id, UUID.randomUUID(), null, null, title, ReplayType.PATTERN,
                null, null, null, List.of(), List.of(), ReplayStatus.DRAFT, 1, 0, 0, 0, null, null, null,
                Instant.EPOCH, Instant.EPOCH);
        return new ReplayRetrievalCandidate(id, response, null, score, lexicalRank, vectorRank, rank);
    }
}
