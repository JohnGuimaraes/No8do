package com.no8do.api.replay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReplayRetrievalEvaluationResultTests {
    @Test void computesUnweightedMacroAverageWithoutPrematureRounding() {
        ReplayRetrievalEvaluationResult result = ReplayRetrievalEvaluationResult.aggregate(ReplayRetrievalStrategy.LEXICAL, 5, List.of(
                new ReplayRetrievalEvaluationCaseResult("first", new ReplayRetrievalMetricsResult(1D, .5D, 1D, .75D)),
                new ReplayRetrievalEvaluationCaseResult("second", new ReplayRetrievalMetricsResult(.2D, 1D, .5D, .25D))));
        assertThat(result.meanPrecisionAtK()).isEqualTo(.6D); assertThat(result.meanRecallAtK()).isEqualTo(.75D);
        assertThat(result.meanMrrAtK()).isEqualTo(.75D); assertThat(result.meanNdcgAtK()).isEqualTo(.5D);
    }
}
