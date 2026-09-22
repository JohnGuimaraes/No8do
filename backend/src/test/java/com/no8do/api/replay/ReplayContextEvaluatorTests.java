package com.no8do.api.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReplayContextEvaluatorTests {
    private final ReplayContextEvaluator evaluator = new ReplayContextEvaluator();

    @Test
    void evaluatesPerfectPackageAndCompactFullComposition() {
        UUID a = id();
        UUID b = id();
        UUID c = id();
        ReplayContextPackage context = packageOf(List.of(compact(b, 2, 30)),
                List.of(full(a, 1, 200), full(c, 3, 100)), 100, 400, 100, 70);

        ReplayContextEvaluationMetrics result = evaluator.evaluate(new ReplayContextEvaluationCase("perfect",
                List.of(a, b, c), context));

        assertThat(result.relevantCount()).isEqualTo(3);
        assertThat(result.packageSourceCount()).isEqualTo(3);
        assertThat(result.relevantIncludedCount()).isEqualTo(3);
        assertThat(result.sourcePrecision()).isEqualTo(1D);
        assertThat(result.sourceRecall()).isEqualTo(1D);
        assertThat(result.compactSourceCount()).isEqualTo(1);
        assertThat(result.fullSourceCount()).isEqualTo(2);
        assertThat(result.relevantCompactCount()).isEqualTo(1);
        assertThat(result.relevantFullCount()).isEqualTo(2);
    }

    @Test
    void evaluatesTheControlledGroundTruthAndManifestExample() {
        UUID a = id();
        UUID b = id();
        UUID c = id();
        UUID d = id();
        UUID x = id();
        ReplayContextPackage context = packageOf(List.of(compact(b, 2, 5), compact(x, 3, 7)),
                List.of(full(a, 1, 10)), 100, 100, 12, 0);

        ReplayContextEvaluationMetrics result = evaluator.evaluate(new ReplayContextEvaluationCase("controlled",
                List.of(a, b, c, d), context));

        assertThat(result.relevantCount()).isEqualTo(4);
        assertThat(result.packageSourceCount()).isEqualTo(3);
        assertThat(result.relevantIncludedCount()).isEqualTo(2);
        assertThat(result.sourcePrecision()).isEqualTo(2D / 3D);
        assertThat(result.sourceRecall()).isEqualTo(0.5D);
        assertThat(result.relevantFullCount()).isEqualTo(1);
        assertThat(result.relevantCompactCount()).isEqualTo(1);
    }

    @Test
    void emptyPackageProducesZeroPrecisionRecallSourcesAndTokens() {
        UUID relevantA = id();
        UUID relevantB = id();
        ReplayContextPackage context = packageOf(List.of(), List.of(), 100, 200, 0, 0);

        ReplayContextEvaluationMetrics result = evaluator.evaluate(new ReplayContextEvaluationCase("empty",
                List.of(relevantA, relevantB), context));

        assertThat(result.sourcePrecision()).isZero();
        assertThat(result.sourceRecall()).isZero();
        assertThat(result.packageSourceCount()).isZero();
        assertThat(result.compactTokens()).isZero();
        assertThat(result.fullSourceTokens()).isZero();
        assertThat(result.totalKnowledgeTokens()).isZero();
    }

    @Test
    void packageWithoutRelevantSourcesHasZeroPrecisionAndRecall() {
        ReplayContextPackage context = packageOf(List.of(compact(id(), 1, 2), compact(id(), 2, 3)),
                List.of(), 20, 20, 5, 0);

        ReplayContextEvaluationMetrics result = evaluator.evaluate(new ReplayContextEvaluationCase("none-relevant",
                List.of(id(), id()), context));

        assertThat(result.relevantIncludedCount()).isZero();
        assertThat(result.sourcePrecision()).isZero();
        assertThat(result.sourceRecall()).isZero();
    }

    @Test
    void computesPrecisionAndRecallForMixedRelevantAndIrrelevantSources() {
        UUID relevantA = id();
        UUID relevantB = id();
        ReplayContextPackage context = packageOf(List.of(compact(relevantA, 1, 2), compact(id(), 2, 3), compact(id(), 3, 4)),
                List.of(), 20, 20, 9, 0);

        ReplayContextEvaluationMetrics result = evaluator.evaluate(new ReplayContextEvaluationCase("mixed",
                List.of(relevantA, relevantB), context));

        assertThat(result.relevantIncludedCount()).isEqualTo(1);
        assertThat(result.sourcePrecision()).isEqualTo(1D / 3D);
        assertThat(result.sourceRecall()).isEqualTo(0.5D);
    }

    @Test
    void exposesRelevantCompactOnlyFullOnlyAndCombinedCounts() {
        UUID compactId = id();
        UUID fullId = id();
        ReplayContextPackage compactPackage = packageOf(List.of(compact(compactId, 1, 4)), List.of(), 10, 10, 4, 0);
        ReplayContextPackage fullPackage = packageOf(List.of(), List.of(full(fullId, 1, 5)), 10, 10, 0, 0);

        ReplayContextEvaluationMetrics compactResult = evaluator.evaluate(new ReplayContextEvaluationCase("compact",
                List.of(compactId), compactPackage));
        ReplayContextEvaluationMetrics fullResult = evaluator.evaluate(new ReplayContextEvaluationCase("full",
                List.of(fullId), fullPackage));

        assertThat(compactResult.relevantCompactCount()).isEqualTo(1);
        assertThat(compactResult.relevantFullCount()).isZero();
        assertThat(fullResult.relevantCompactCount()).isZero();
        assertThat(fullResult.relevantFullCount()).isEqualTo(1);
    }

    @Test
    void preservesPackageTokenAccountingReplacementAndBudgetUtilization() {
        UUID a = id();
        UUID b = id();
        ReplayContextPackage context = packageOf(List.of(compact(b, 2, 30)), List.of(full(a, 1, 200)),
                100, 400, 100, 70);

        ReplayContextEvaluationMetrics result = evaluator.evaluate(new ReplayContextEvaluationCase("tokens",
                List.of(a, b), context));

        assertThat(result.compactTokens()).isEqualTo(30);
        assertThat(result.fullSourceTokens()).isEqualTo(200);
        assertThat(result.totalKnowledgeTokens()).isEqualTo(230);
        assertThat(result.replacedCompactTokens()).isEqualTo(70);
        assertThat(result.originalCompactSelectedTokens()).isEqualTo(100);
        assertThat(result.compactAvailableTokens()).isEqualTo(100);
        assertThat(result.expansionAvailableTokens()).isEqualTo(400);
        assertThat(result.compactBudgetUtilization()).isEqualTo(0.3D);
        assertThat(result.expansionBudgetUtilization()).isEqualTo(0.5D);
    }

    @Test
    void computesUnweightedMacroAveragesAcrossAtLeastThreeCases() {
        UUID a = id();
        UUID b = id();
        UUID x = id();
        ReplayContextPackage firstPackage = packageOf(List.of(compact(x, 2, 4)), List.of(full(a, 1, 6)),
                8, 12, 4, 0);
        ReplayContextPackage secondPackage = packageOf(List.of(compact(b, 1, 2)), List.of(full(x, 2, 18)),
                8, 24, 2, 0);
        ReplayContextPackage emptyPackage = packageOf(List.of(), List.of(), 8, 24, 0, 0);
        List<ReplayContextEvaluationCase> cases = List.of(
                new ReplayContextEvaluationCase("first", List.of(a, b), firstPackage),
                new ReplayContextEvaluationCase("second", List.of(b, x), secondPackage),
                new ReplayContextEvaluationCase("empty", List.of(a), emptyPackage));

        ReplayContextEvaluationReport report = evaluator.evaluateAll(cases);

        assertThat(report.cases()).hasSize(3);
        assertThat(report.macroAverageSourcePrecision()).isEqualTo(0.5D);
        assertThat(report.macroAverageSourceRecall()).isEqualTo(0.5D);
        assertThat(report.averageTotalKnowledgeTokens()).isEqualTo(10D);
        assertThat(report.averageCompactBudgetUtilization()).isEqualTo(0.25D);
        assertThat(report.averageExpansionBudgetUtilization()).isEqualTo((0.5D + 0.75D) / 3D);
    }

    @Test
    void isDeterministicAndReturnsImmutableDatasetCases() {
        UUID relevant = id();
        ReplayContextPackage context = packageOf(List.of(compact(relevant, 1, 5)), List.of(), 10, 20, 5, 0);
        List<ReplayContextEvaluationCase> cases = List.of(new ReplayContextEvaluationCase("stable", List.of(relevant), context));

        ReplayContextEvaluationReport first = evaluator.evaluateAll(cases);
        ReplayContextEvaluationReport second = evaluator.evaluateAll(cases);

        assertThat(first).isEqualTo(second);
        assertThatThrownBy(() -> first.cases().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullInputsEmptyGroundTruthNullIdsAndDuplicates() {
        ReplayContextPackage context = packageOf(List.of(), List.of(), 10, 10, 0, 0);
        assertThatThrownBy(() -> evaluator.evaluate(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.evaluateAll(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.evaluateAll(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayContextEvaluationCase("null-package", List.of(id()), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayContextEvaluationCase("empty-ground-truth", List.of(), context))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayContextEvaluationCase("null-ground-truth", (java.util.Collection<UUID>) null, context))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayContextEvaluationCase("null-id", java.util.Arrays.asList(id(), null), context))
                .isInstanceOf(IllegalArgumentException.class);
        UUID duplicate = id();
        assertThatThrownBy(() -> new ReplayContextEvaluationCase("duplicate", List.of(duplicate, duplicate), context))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicatas");
    }

    private ReplayContextPackage packageOf(List<ReplayBudgetedContextEntry> compact,
            List<ReplayFullSourceEntry> full, int compactCapacity, int expansionCapacity,
            long originalCompactTokens, long replacedCompactTokens) {
        long compactTokens = compact.stream().mapToLong(ReplayBudgetedContextEntry::estimatedTokens).sum();
        long fullTokens = full.stream().mapToLong(ReplayFullSourceEntry::estimatedTokens).sum();
        List<ReplayContextSource> manifest = new ArrayList<>();
        for (ReplayBudgetedContextEntry entry : compact) {
            ReplayCompactRepresentation value = entry.representation();
            manifest.add(new ReplayContextSource(value.replayId(), value.retrievalRank(), value.hybridScore(),
                    value.lexicalRank(), value.vectorRank(), ReplayContextRepresentationType.COMPACT));
        }
        for (ReplayFullSourceEntry entry : full) {
            ReplayFullSourceRepresentation value = entry.representation();
            manifest.add(new ReplayContextSource(value.replayId(), value.retrievalRank(), value.hybridScore(),
                    value.lexicalRank(), value.vectorRank(), ReplayContextRepresentationType.FULL));
        }
        manifest.sort(Comparator.comparingInt(ReplayContextSource::retrievalRank));
        ReplayContextTokenAccounting accounting = new ReplayContextTokenAccounting(compactTokens, fullTokens,
                compactTokens + fullTokens, compactCapacity, expansionCapacity,
                originalCompactTokens, replacedCompactTokens);
        return new ReplayContextPackage(id(), "query untouched", compact, full, manifest, accounting);
    }

    private ReplayBudgetedContextEntry compact(UUID id, int rank, int tokens) {
        return new ReplayBudgetedContextEntry(new ReplayCompactRepresentation(id, rank, 0.75, 1, null,
                "compact " + id, false), tokens);
    }

    private ReplayFullSourceEntry full(UUID id, int rank, int tokens) {
        return new ReplayFullSourceEntry(new ReplayFullSourceRepresentation(id, rank, 0.75, null, 1,
                "full " + id), tokens);
    }

    private UUID id() { return UUID.randomUUID(); }
}
