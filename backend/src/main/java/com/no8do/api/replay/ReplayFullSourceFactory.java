package com.no8do.api.replay;

import java.util.ArrayList;
import java.util.List;

/** Projects a retrieved source into full, semantic-only, untruncated text. */
public final class ReplayFullSourceFactory {
    public ReplayFullSourceRepresentation full(ReplayRetrievalCandidate candidate) {
        ReplaySemanticContent fields = ReplaySemanticContent.from(candidate);
        List<String> lines = new ArrayList<>();
        appendText(lines, "Title", fields.title());
        appendText(lines, "Type", fields.type().name());
        appendList(lines, "Tags", fields.tags());
        appendList(lines, "Stack", fields.stack());
        appendText(lines, "Problem", fields.problem());
        appendText(lines, "Context", fields.context());
        appendText(lines, "Solution", fields.solution());
        return new ReplayFullSourceRepresentation(candidate.replayId(), candidate.retrievalRank(),
                candidate.hybridScore(), candidate.lexicalRank(), candidate.vectorRank(), String.join("\n", lines));
    }

    private void appendText(List<String> lines, String label, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) lines.add(label + ": " + normalized);
    }

    private void appendList(List<String> lines, String label, List<String> values) {
        if (values == null || values.isEmpty()) return;
        List<String> normalized = values.stream().map(this::trimToNull).filter(value -> value != null).toList();
        if (!normalized.isEmpty()) lines.add(label + ": " + String.join(", ", normalized));
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
