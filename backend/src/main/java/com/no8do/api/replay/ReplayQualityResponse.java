package com.no8do.api.replay;
import java.util.List;
public record ReplayQualityResponse(int score, ReplayQualityLevel level, int usageCount, int successCount, int failureCount, Integer successRate, List<String> signals) {}
