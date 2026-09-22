package com.no8do.api.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReplayRetrievalMetricsTests {
    @Test void calculatesBinaryMetricsWithExplicitFormula() {
        UUID a = UUID.randomUUID(); UUID b = UUID.randomUUID(); UUID c = UUID.randomUUID(); UUID d = UUID.randomUUID();
        ReplayRetrievalMetricsResult result = ReplayRetrievalMetrics.calculate(List.of(a, b, c, d), Set.of(b, d), 3);
        double expectedNdcg = (1D / log2(3)) / (1D + (1D / log2(3)));
        assertThat(result.precisionAtK()).isEqualTo(1D / 3D); assertThat(result.recallAtK()).isEqualTo(0.5D);
        assertThat(result.mrrAtK()).isEqualTo(0.5D); assertThat(result.ndcgAtK()).isCloseTo(expectedNdcg, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test void handlesAllRelevantNoneRetrievedFirstRelevantAndShortRanking() {
        UUID a = UUID.randomUUID(); UUID b = UUID.randomUUID(); UUID c = UUID.randomUUID();
        assertThat(ReplayRetrievalMetrics.calculate(List.of(a, b), Set.of(a, b), 3)).isEqualTo(new ReplayRetrievalMetricsResult(2D / 3D, 1D, 1D, 1D));
        assertThat(ReplayRetrievalMetrics.calculate(List.of(a, b), Set.of(c), 3)).isEqualTo(new ReplayRetrievalMetricsResult(0D, 0D, 0D, 0D));
        assertThat(ReplayRetrievalMetrics.calculate(List.of(a, b), Set.of(a), 1)).isEqualTo(new ReplayRetrievalMetricsResult(1D, 1D, 1D, 1D));
    }

    @Test void rejectsInvalidKAndDuplicateRanking() {
        UUID a = UUID.randomUUID();
        assertThatThrownBy(() -> ReplayRetrievalMetrics.calculate(List.of(a), Set.of(a), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReplayRetrievalMetrics.calculate(List.of(a, a), Set.of(a), 2)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicado");
    }

    private static double log2(int value) { return Math.log(value) / Math.log(2D); }
}
