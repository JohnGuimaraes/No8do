package com.no8do.api.replay;

import java.util.List;

public record FindSimilarReplaysRequest(String query, String title, String problem, List<String> tags, List<String> stack, ReplayType type) {}
