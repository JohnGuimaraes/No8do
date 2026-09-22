package com.no8do.api.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReplayCompactRepresentationFactoryTests {
    private final ReplayCompactRepresentationFactory factory = new ReplayCompactRepresentationFactory();
    private final ReplayCompactRepresentationPolicy policy = new ReplayCompactRepresentationPolicy(40, 3, 3, 40, 40, 40);

    @Test
    void compactsLexicalOnlyCandidateFromReplayResponse() {
        UUID id = UUID.randomUUID();
        ReplayCompactRepresentation result = factory.compact(candidate(id, response(id, "Resposta", List.of("a", "b"), List.of("Java"), "Problem", "Context", "Solution"), null, 0.625, 2, null, 1), policy);
        assertThat(result.content()).isEqualTo("Title: Resposta\nType: PATTERN\nTags: a, b\nStack: Java\nProblem: Problem\nContext: Context\nSolution: Solution");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void compactsVectorOnlyCandidateFromCurrentVersionSnapshot() {
        UUID id = UUID.randomUUID();
        ReplayCompactRepresentation result = factory.compact(candidate(id, null, version(id, "Snapshot atual"), 0.5, null, 4, 1), policy);
        assertThat(result.content()).contains("Title: Snapshot atual");
        assertThat(result.content()).doesNotContain("operacional");
    }

    @Test
    void prefersVersionSnapshotWhenHybridCandidateHasBothSources() {
        UUID id = UUID.randomUUID();
        ReplayCompactRepresentation result = factory.compact(candidate(id, response(id, "Resposta antiga", List.of(), List.of(), null, null, null), version(id, "Snapshot atual"), 0.75, 1, 2, 1), policy);
        assertThat(result.content()).contains("Title: Snapshot atual").doesNotContain("Resposta antiga");
        assertThat(result.replayId()).isEqualTo(id);
    }

    @Test
    void rejectsDivergentSourceIdentityAndMissingOrInsufficientContent() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> factory.compact(candidate(id, response(UUID.randomUUID(), "Outro Replay", List.of(), List.of(), null, null, null), null, 0.2, 1, null, 1), policy))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Identidade");
        assertThatThrownBy(() -> factory.compact(candidate(id, response(id, "Resposta", List.of(), List.of(), null, null, null), version(UUID.randomUUID(), "Outro snapshot"), 0.2, 1, 1, 1), policy))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Identidade");
        assertThatThrownBy(() -> factory.compact(candidate(id, null, null, 0.2, 1, null, 1), policy))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sem fonte");
        assertThatThrownBy(() -> factory.compact(candidate(id, response(id, "  ", List.of(), List.of(), null, null, null), null, 0.2, 1, null, 1), policy))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("suficiente");
    }

    @Test
    void truncatesTextWithinCodePointLimitsAndMarksTheRepresentation() {
        UUID id = UUID.randomUUID();
        ReplayCompactRepresentationPolicy tight = new ReplayCompactRepresentationPolicy(10, 2, 2, 7, 7, 7);
        ReplayCompactRepresentation result = factory.compact(candidate(id, response(id, "Título muito comprido", List.of(), List.of(), "Problema extenso", "Contexto extenso", "Solução extensa"), null, 0.3, 1, null, 1), tight);
        assertThat(result.content()).contains("Title: Título mu…").contains("Problem: Proble…").contains("Context: Contex…").contains("Solution: Soluçã…");
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void limitsTagsAndStackWithoutReorderingThem() {
        UUID id = UUID.randomUUID();
        ReplayCompactRepresentationPolicy limited = new ReplayCompactRepresentationPolicy(40, 2, 2, 40, 40, 40);
        ReplayCompactRepresentation result = factory.compact(candidate(id, response(id, "Título", List.of("zeta", "alpha", "middle"), List.of("Kotlin", "Java", "Go"), null, null, null), null, 0.3, 1, null, 1), limited);
        assertThat(result.content()).contains("Tags: zeta, alpha").contains("Stack: Kotlin, Java").doesNotContain("middle", "Go");
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void omitsEmptyFieldsAndPreservesInternalWhitespace() {
        UUID id = UUID.randomUUID();
        ReplayCompactRepresentation result = factory.compact(candidate(id, response(id, " Título ", List.of(" "), List.of(), "  ", " linha 1\n  linha 2 ", null), null, 0.4, 1, null, 1), policy);
        assertThat(result.content()).isEqualTo("Title: Título\nType: PATTERN\nContext: linha 1\n  linha 2");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void truncatesUnicodeWithoutSplittingSurrogatePairsAndCountsMarker() {
        UUID id = UUID.randomUUID();
        ReplayCompactRepresentationPolicy unicodePolicy = new ReplayCompactRepresentationPolicy(9, 2, 2, 20, 20, 20);
        ReplayCompactRepresentation result = factory.compact(candidate(id, response(id, "Java 🚀 arquitetura", List.of(), List.of(), null, null, null), null, 0.6, 1, null, 1), unicodePolicy);
        String title = result.content().lines().findFirst().orElseThrow().substring("Title: ".length());
        assertThat(title).isEqualTo("Java 🚀 a…");
        assertThat(title.codePointCount(0, title.length())).isEqualTo(9);
        assertThat(hasUnpairedSurrogate(title)).isFalse();
    }

    @Test
    void preservesProvenanceAndProducesEqualDeterministicResults() {
        UUID id = UUID.randomUUID();
        ReplayRetrievalCandidate candidate = candidate(id, response(id, "Título", List.of("tag"), List.of("Java"), "Problema", "Contexto", "Solução"), null, 0.73125, 3, 2, 7);
        ReplayCompactRepresentation first = factory.compact(candidate, policy);
        ReplayCompactRepresentation second = factory.compact(candidate, policy);
        assertThat(first).isEqualTo(second);
        assertThat(first.replayId()).isEqualTo(id);
        assertThat(first.retrievalRank()).isEqualTo(7);
        assertThat(first.hybridScore()).isEqualTo(0.73125);
        assertThat(first.lexicalRank()).isEqualTo(3);
        assertThat(first.vectorRank()).isEqualTo(2);
    }

    @Test
    void compactAllPreservesRetrievalOrderAndReturnsImmutableList() {
        UUID workspaceId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        ReplayRetrievalCandidate rankOne = candidate(firstId, null, version(firstId, "One"), 0.9, null, 1, 1);
        UUID secondId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();
        ReplayRetrievalCandidate rankTwo = candidate(secondId, response(secondId, "Two", List.of(), List.of(), null, null, null), null, 0.8, 2, null, 2);
        ReplayRetrievalCandidate rankThree = candidate(thirdId, response(thirdId, "Three", List.of(), List.of(), null, null, null), null, 0.7, 3, null, 3);
        ReplayRetrievalResult allContent = new ReplayRetrievalResult(workspaceId, "query", 3, 3, List.of(rankTwo, rankOne, rankThree));

        List<ReplayCompactRepresentation> compact = factory.compactAll(allContent, policy);

        assertThat(compact).extracting(ReplayCompactRepresentation::retrievalRank).containsExactly(2, 1, 3);
        assertThatThrownBy(() -> compact.add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void contentContainsOnlyAllowedSemanticFields() {
        UUID id = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ReplayResponse response = new ReplayResponse(id, workspaceId, null, null, "Permitido", ReplayType.PATTERN,
                "problem allowed", "solution allowed", "context allowed", List.of("tag allowed"), List.of("stack allowed"),
                ReplayStatus.VALIDATED, 9, 500, 400, 100, Instant.parse("2026-01-01T00:00:00Z"), userId,
                "User Sensitive", Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        ReplayCompactRepresentation compact = factory.compact(candidate(id, response, null, 0.5, 1, null, 1), policy);

        assertThat(compact.content()).isEqualTo("Title: Permitido\nType: PATTERN\nTags: tag allowed\nStack: stack allowed\n"
                + "Problem: problem allowed\nContext: context allowed\nSolution: solution allowed")
                .doesNotContain(workspaceId.toString(), userId.toString(), "User Sensitive", "VALIDATED", "Quality Score",
                        "usage", "embedding", "content hash", "2026-", "2025-");
    }

    @Test
    void requiresValidPolicyAndCandidateProvenance() {
        assertThatThrownBy(() -> new ReplayCompactRepresentationPolicy(0, 1, 1, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplayCompactRepresentationPolicy(1, 1, 1, -1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> factory.compact(candidate(id, response(id, "Título", List.of(), List.of(), null, null, null), null, Double.NaN, null, null, 1), policy))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.compact(candidate(id, response(id, "Título", List.of(), List.of(), null, null, null), null, 0.2, null, null, 1), policy))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ReplayResponse response(UUID id, String title, List<String> tags, List<String> stack,
            String problem, String context, String solution) {
        return new ReplayResponse(id, UUID.randomUUID(), null, null, title, ReplayType.PATTERN, problem, solution,
                context, tags, stack, ReplayStatus.DRAFT, 1, 0, 0, 0, null, null, null, Instant.EPOCH, Instant.EPOCH);
    }

    private ReplayVersion version(UUID replayId, String title) {
        Replay replay = mock(Replay.class);
        when(replay.getId()).thenReturn(replayId);
        ReplayVersion version = mock(ReplayVersion.class);
        when(version.getReplay()).thenReturn(replay);
        when(version.getTitle()).thenReturn(title);
        when(version.getType()).thenReturn(ReplayType.PATTERN);
        when(version.getTags()).thenReturn(new String[] { "snapshot-tag" });
        when(version.getStack()).thenReturn(new String[] { "snapshot-stack" });
        when(version.getProblem()).thenReturn("snapshot-problem");
        when(version.getContext()).thenReturn("snapshot-context");
        when(version.getSolution()).thenReturn("snapshot-solution");
        return version;
    }

    private ReplayRetrievalCandidate candidate(UUID id, ReplayResponse response, ReplayVersion version,
            double score, Integer lexicalRank, Integer vectorRank, int retrievalRank) {
        return new ReplayRetrievalCandidate(id, response, version, score, lexicalRank, vectorRank, retrievalRank);
    }

    private boolean hasUnpairedSurrogate(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) return true;
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return true;
            }
        }
        return false;
    }
}
