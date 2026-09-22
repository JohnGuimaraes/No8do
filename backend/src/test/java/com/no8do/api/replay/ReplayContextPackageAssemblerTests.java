package com.no8do.api.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReplayContextPackageAssemblerTests {
    private final ReplayContextPackageAssembler assembler = new ReplayContextPackageAssembler();

    @Test
    void replacesExpandedCompactsAndAccountsForOnlyTheFinalRepresentations() {
        Fixture fixture = fixture();
        ReplayBudgetedContextResult budgeted = budgeted(List.of(fixture.compactA(50), fixture.compactB(30), fixture.compactC(20)),
                List.of(), 100);
        ReplaySourceExpansionResult expansion = expansion(List.of(fixture.fullA(200), fixture.fullC(100)), List.of(), 400);

        ReplayContextPackage result = assembler.assemble(fixture.retrieval(), budgeted, expansion);

        assertThat(result.compactSources()).containsExactly(budgeted.selectedEntries().get(1));
        assertThat(result.fullSources()).containsExactlyElementsOf(expansion.selectedSources());
        assertThat(result.sourceManifest()).containsExactly(
                fixture.manifest(fixture.a(), 1, 0.91, 1, 2, ReplayContextRepresentationType.FULL),
                fixture.manifest(fixture.b(), 2, 0.82, 2, null, ReplayContextRepresentationType.COMPACT),
                fixture.manifest(fixture.c(), 3, 0.73, null, 1, ReplayContextRepresentationType.FULL));
        assertThat(result.tokenAccounting()).isEqualTo(new ReplayContextTokenAccounting(30, 300, 330,
                100, 400, 100, 70));
        assertThat(result.workspaceId()).isEqualTo(fixture.workspaceId());
        assertThat(result.query()).isEqualTo("  exact query  ");
    }

    @Test
    void keepsAllSelectedCompactsWhenThereAreNoFullSources() {
        Fixture fixture = fixture();
        ReplayBudgetedContextResult budgeted = budgeted(List.of(fixture.compactA(50), fixture.compactB(30)), List.of(), 100);

        ReplayContextPackage result = assembler.assemble(fixture.retrieval(), budgeted,
                expansion(List.of(), List.of(), 400));

        assertThat(result.compactSources()).containsExactlyElementsOf(budgeted.selectedEntries());
        assertThat(result.fullSources()).isEmpty();
        assertThat(result.sourceManifest()).extracting(ReplayContextSource::representationType)
                .containsExactly(ReplayContextRepresentationType.COMPACT, ReplayContextRepresentationType.COMPACT);
        assertThat(result.tokenAccounting()).isEqualTo(new ReplayContextTokenAccounting(80, 0, 80, 100, 400, 80, 0));
    }

    @Test
    void keepsOnlyFullSourcesWhenEverySelectedCompactWasExpanded() {
        Fixture fixture = fixture();
        ReplayBudgetedContextResult budgeted = budgeted(List.of(fixture.compactA(50), fixture.compactB(30)), List.of(), 100);
        ReplaySourceExpansionResult expansion = expansion(List.of(fixture.fullA(80), fixture.fullB(90)), List.of(), 400);

        ReplayContextPackage result = assembler.assemble(fixture.retrieval(), budgeted, expansion);

        assertThat(result.compactSources()).isEmpty();
        assertThat(result.fullSources()).containsExactlyElementsOf(expansion.selectedSources());
        assertThat(result.sourceManifest()).extracting(ReplayContextSource::representationType)
                .containsExactly(ReplayContextRepresentationType.FULL, ReplayContextRepresentationType.FULL);
        assertThat(result.tokenAccounting()).isEqualTo(new ReplayContextTokenAccounting(0, 170, 170, 100, 400, 80, 80));
    }

    @Test
    void excludesBothCompactAndExpansionSkippedContent() {
        Fixture fixture = fixture();
        ReplayBudgetedContextResult budgeted = budgeted(List.of(fixture.compactA(50)),
                List.of(fixture.compactB(30)), 100);
        ReplaySourceExpansionResult expansion = expansion(List.of(fixture.fullA(80)),
                List.of(new ReplaySkippedFullSource(fixture.fullCRepresentation(), 900, ReplaySourceSkipReason.TOKEN_BUDGET)), 1000);

        ReplayContextPackage result = assembler.assemble(fixture.retrieval(), budgeted, expansion);

        assertThat(result.compactSources()).isEmpty();
        assertThat(result.fullSources()).extracting(entry -> entry.representation().replayId()).containsExactly(fixture.a());
        assertThat(result.sourceManifest()).extracting(ReplayContextSource::replayId).containsExactly(fixture.a())
                .doesNotContain(fixture.b(), fixture.c());
        assertThat(result.toString()).doesNotContain(budgeted.skippedEntries().getFirst().representation().content(),
                expansion.skippedSources().getFirst().representation().content());
        assertThat(result.tokenAccounting()).isEqualTo(new ReplayContextTokenAccounting(0, 80, 80, 100, 1000, 50, 50));
    }

    @Test
    void allowsAValidEmptyContextPackage() {
        Fixture fixture = fixture();

        ReplayContextPackage result = assembler.assemble(fixture.retrieval(), budgeted(List.of(), List.of(), 100),
                expansion(List.of(), List.of(), 400));

        assertThat(result.compactSources()).isEmpty();
        assertThat(result.fullSources()).isEmpty();
        assertThat(result.sourceManifest()).isEmpty();
        assertThat(result.tokenAccounting()).isEqualTo(new ReplayContextTokenAccounting(0, 0, 0, 100, 400, 0, 0));
    }

    @Test
    void rejectsFullSourcesNotDerivedFromSelectedCompactsIncludingPreviouslySkippedOnes() {
        Fixture fixture = fixture();
        ReplayBudgetedContextResult selectedA = budgeted(List.of(fixture.compactA(50)), List.of(fixture.compactB(30)), 100);
        ReplaySourceExpansionResult fromSkipped = expansion(List.of(fixture.fullB(80)), List.of(), 400);
        ReplaySourceExpansionResult unknown = expansion(List.of(fixture.full(UUID.randomUUID(), 8, 20, 0.5, 1, null)), List.of(), 400);

        assertThatThrownBy(() -> assembler.assemble(fixture.retrieval(), selectedA, fromSkipped))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não corresponde a compact selecionado");
        assertThatThrownBy(() -> assembler.assemble(fixture.retrieval(), selectedA, unknown))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não corresponde a compact selecionado");
    }

    @Test
    void rejectsReplayIdAndRetrievalRankMismatches() {
        Fixture fixture = fixture();
        ReplayBudgetedContextResult budgeted = budgeted(List.of(fixture.compactA(50)), List.of(), 100);

        assertThatThrownBy(() -> assembler.assemble(fixture.retrieval(), budgeted,
                expansion(List.of(fixture.full(UUID.randomUUID(), 1, 80, 0.91, 1, 2)), List.of(), 400)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não corresponde a compact selecionado");
        assertThatThrownBy(() -> assembler.assemble(fixture.retrieval(), budgeted,
                expansion(List.of(fixture.fullA(4, 80)), List.of(), 400)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retrievalRank");
    }

    @Test
    void rejectsDuplicateCompactAndFullSources() {
        Fixture fixture = fixture();
        ReplayBudgetedContextEntry compactA = fixture.compactA(50);
        ReplayBudgetedContextResult duplicateCompacts = budgeted(List.of(compactA, compactA), List.of(), 200);
        ReplaySourceExpansionResult duplicateFull = expansion(List.of(fixture.fullA(80), fixture.fullA(80)), List.of(), 400);

        assertThatThrownBy(() -> assembler.assemble(fixture.retrieval(), duplicateCompacts, expansion(List.of(), List.of(), 400)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("compact selecionado duplicado");
        assertThatThrownBy(() -> assembler.assemble(fixture.retrieval(), budgeted(List.of(compactA), List.of(), 100), duplicateFull))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("full source duplicada");
    }

    @Test
    void rejectsCompactSourcesThatDoNotMatchRetrieval() {
        Fixture fixture = fixture();
        ReplayCompactRepresentation orphan = fixture.compact(UUID.randomUUID(), 1, 10, 0.1, 1, null).representation();

        assertThatThrownBy(() -> assembler.assemble(fixture.retrieval(), budgeted(List.of(new ReplayBudgetedContextEntry(orphan, 1)), List.of(), 10),
                expansion(List.of(), List.of(), 400)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não pertence ao retrieval");
    }

    @Test
    void rejectsDivergentHybridAndLexicalOrVectorProvenance() {
        Fixture fixture = fixture();
        ReplayBudgetedContextResult budgeted = budgeted(List.of(fixture.compactA(50)), List.of(), 100);

        assertThatThrownBy(() -> assembler.assemble(fixture.retrieval(), budgeted,
                expansion(List.of(fixture.full(fixture.a(), 1, 80, 0.12, 1, 2)), List.of(), 400)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("provenance diverge");
        assertThatThrownBy(() -> assembler.assemble(fixture.retrieval(), budgeted,
                expansion(List.of(fixture.full(fixture.a(), 1, 80, 0.91, 9, 2)), List.of(), 400)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("provenance diverge");
        assertThatThrownBy(() -> assembler.assemble(fixture.retrieval(), budgeted,
                expansion(List.of(fixture.full(fixture.a(), 1, 80, 0.91, 1, 9)), List.of(), 400)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("provenance diverge");
    }

    @Test
    void preservesExactProvenanceAndOrdersManifestByRetrievalRank() {
        Fixture fixture = fixture();
        ReplayBudgetedContextResult budgeted = budgeted(List.of(fixture.compactC(20), fixture.compactA(50)), List.of(), 100);
        ReplaySourceExpansionResult expansion = expansion(List.of(fixture.fullC(100)), List.of(), 400);

        ReplayContextPackage result = assembler.assemble(fixture.retrieval(), budgeted, expansion);

        assertThat(result.compactSources()).extracting(entry -> entry.representation().replayId()).containsExactly(fixture.a());
        assertThat(result.sourceManifest()).containsExactly(
                fixture.manifest(fixture.a(), 1, 0.91, 1, 2, ReplayContextRepresentationType.COMPACT),
                fixture.manifest(fixture.c(), 3, 0.73, null, 1, ReplayContextRepresentationType.FULL));
    }

    @Test
    void rejectsAccountingThatIsNegativeOverCapacityOrMathematicallyInconsistent() {
        assertThatThrownBy(() -> new ReplayContextTokenAccounting(-1, 0, 0, 10, 10, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayContextTokenAccounting(11, 0, 11, 10, 10, 11, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("compactTokens");
        assertThatThrownBy(() -> new ReplayContextTokenAccounting(0, 0, 0, 10, 10, 11, 11))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("originalCompactSelectedTokens");
        assertThatThrownBy(() -> new ReplayContextTokenAccounting(0, 11, 11, 10, 10, 0, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fullSourceTokens");
        assertThatThrownBy(() -> new ReplayContextTokenAccounting(9, 2, 11, 10, 10, 10, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("substituição");
    }

    @Test
    void rejectsNullInputsAndInvalidPackageIdentity() {
        Fixture fixture = fixture();
        ReplayBudgetedContextResult budgeted = budgeted(List.of(), List.of(), 100);
        ReplaySourceExpansionResult expansion = expansion(List.of(), List.of(), 400);

        assertThatThrownBy(() -> assembler.assemble(null, budgeted, expansion)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assembler.assemble(fixture.retrieval(), null, expansion)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assembler.assemble(fixture.retrieval(), budgeted, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayContextPackage(null, "q", List.of(), List.of(), List.of(),
                new ReplayContextTokenAccounting(0, 0, 0, 1, 1, 0, 0))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void packageListsAreImmutableAndAssemblyIsDeterministic() {
        Fixture fixture = fixture();
        ReplayBudgetedContextResult budgeted = budgeted(List.of(fixture.compactB(30), fixture.compactA(50)), List.of(), 100);
        ReplaySourceExpansionResult expansion = expansion(List.of(fixture.fullA(200)), List.of(), 400);
        ReplayContextPackage first = assembler.assemble(fixture.retrieval(), budgeted, expansion);
        ReplayContextPackage second = assembler.assemble(fixture.retrieval(), budgeted, expansion);

        assertThat(first).isEqualTo(second);
        assertThatThrownBy(() -> first.compactSources().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.fullSources().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.sourceManifest().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new ReplayContextPackage(first.workspaceId(), first.query(), first.compactSources(),
                first.fullSources(), List.of(first.sourceManifest().getFirst(), first.sourceManifest().getFirst()),
                first.tokenAccounting())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicado");
    }

    private ReplayBudgetedContextResult budgeted(List<ReplayBudgetedContextEntry> selected,
            List<ReplayBudgetedContextEntry> skipped, int availableTokens) {
        long used = selected.stream().mapToLong(ReplayBudgetedContextEntry::estimatedTokens).sum();
        int capacity = Math.max(availableTokens, Math.toIntExact(used));
        ReplayContextBudget budget = new ReplayContextBudget(capacity, 0);
        return new ReplayBudgetedContextResult(budget, selected, skipped, Math.toIntExact(used), capacity - Math.toIntExact(used));
    }

    private ReplaySourceExpansionResult expansion(List<ReplayFullSourceEntry> selected,
            List<ReplaySkippedFullSource> skipped, int capacity) {
        long used = selected.stream().mapToLong(ReplayFullSourceEntry::estimatedTokens).sum();
        int budget = Math.max(capacity, Math.toIntExact(used));
        return new ReplaySourceExpansionResult(new ReplaySourceExpansionPolicy(10, budget), selected, skipped,
                Math.toIntExact(used), budget - Math.toIntExact(used));
    }

    private Fixture fixture() {
        UUID workspace = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<ReplayRetrievalCandidate> candidates = List.of(candidate(workspace, a, 1, 0.91, 1, 2),
                candidate(workspace, b, 2, 0.82, 2, null), candidate(workspace, c, 3, 0.73, null, 1));
        ReplayRetrievalResult retrieval = new ReplayRetrievalResult(workspace, "  exact query  ", 10, 3, candidates);
        return new Fixture(workspace, a, b, c, retrieval);
    }

    private ReplayRetrievalCandidate candidate(UUID workspace, UUID id, int rank, double score,
            Integer lexicalRank, Integer vectorRank) {
        ReplayResponse response = new ReplayResponse(id, workspace, null, null, "Source " + rank, ReplayType.PATTERN,
                "problem", "solution", "context", List.of(), List.of(), ReplayStatus.VALIDATED, 1, 0, 0, 0,
                null, null, null, Instant.EPOCH, Instant.EPOCH);
        return new ReplayRetrievalCandidate(id, response, null, score, lexicalRank, vectorRank, rank);
    }

    private ReplayBudgetedContextEntry compact(UUID id, int rank, int tokens, double score,
            Integer lexicalRank, Integer vectorRank) {
        ReplayCompactRepresentation representation = new ReplayCompactRepresentation(id, rank, score, lexicalRank,
                vectorRank, "compact " + id, false);
        return new ReplayBudgetedContextEntry(representation, tokens);
    }

    private ReplayFullSourceEntry full(UUID id, int rank, int tokens, double score,
            Integer lexicalRank, Integer vectorRank) {
        ReplayFullSourceRepresentation representation = new ReplayFullSourceRepresentation(id, rank, score,
                lexicalRank, vectorRank, "full " + id);
        return new ReplayFullSourceEntry(representation, tokens);
    }

    private ReplayContextSource manifest(UUID id, int rank, double score, Integer lexicalRank,
            Integer vectorRank, ReplayContextRepresentationType type) {
        return new ReplayContextSource(id, rank, score, lexicalRank, vectorRank, type);
    }

    private final class Fixture {
        private final UUID workspaceId;
        private final UUID a;
        private final UUID b;
        private final UUID c;
        private final ReplayRetrievalResult retrieval;

        private Fixture(UUID workspaceId, UUID a, UUID b, UUID c, ReplayRetrievalResult retrieval) {
            this.workspaceId = workspaceId;
            this.a = a;
            this.b = b;
            this.c = c;
            this.retrieval = retrieval;
        }

        UUID workspaceId() { return workspaceId; }
        UUID a() { return a; }
        UUID b() { return b; }
        UUID c() { return c; }
        ReplayRetrievalResult retrieval() { return retrieval; }
        ReplayBudgetedContextEntry compactA(int tokens) { return compact(a, 1, tokens, 0.91, 1, 2); }
        ReplayBudgetedContextEntry compactB(int tokens) { return compact(b, 2, tokens, 0.82, 2, null); }
        ReplayBudgetedContextEntry compactC(int tokens) { return compact(c, 3, tokens, 0.73, null, 1); }
        ReplayBudgetedContextEntry compact(UUID id, int rank, int tokens, double score, Integer lexical, Integer vector) {
            return ReplayContextPackageAssemblerTests.this.compact(id, rank, tokens, score, lexical, vector);
        }
        ReplayFullSourceEntry fullA(int tokens) { return fullA(1, tokens); }
        ReplayFullSourceEntry fullA(int rank, int tokens) { return full(a, rank, tokens, 0.91, 1, 2); }
        ReplayFullSourceEntry fullB(int tokens) { return full(b, 2, tokens, 0.82, 2, null); }
        ReplayFullSourceEntry fullC(int tokens) { return full(c, 3, tokens, 0.73, null, 1); }
        ReplayFullSourceEntry full(UUID id, int rank, int tokens, double score, Integer lexical, Integer vector) {
            return ReplayContextPackageAssemblerTests.this.full(id, rank, tokens, score, lexical, vector);
        }
        ReplayFullSourceRepresentation fullCRepresentation() { return fullC(80).representation(); }
        ReplayContextSource manifest(UUID id, int rank, double score, Integer lexical, Integer vector,
                ReplayContextRepresentationType type) {
            return ReplayContextPackageAssemblerTests.this.manifest(id, rank, score, lexical, vector, type);
        }
    }
}
