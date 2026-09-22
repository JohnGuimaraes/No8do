package com.no8do.api.replay;

public record ReplayRetrievalMetricsResult(double precisionAtK, double recallAtK, double mrrAtK, double ndcgAtK) {}
