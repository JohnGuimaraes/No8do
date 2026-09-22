package com.no8do.api.replay;

import java.util.ArrayList;
import java.util.List;

/** Pure projection of retrieval candidates into bounded, provider-agnostic text. */
public final class ReplayCompactRepresentationFactory {
    public ReplayCompactRepresentation compact(ReplayRetrievalCandidate candidate,
            ReplayCompactRepresentationPolicy policy) {
        if (candidate == null) throw new IllegalArgumentException("candidate é obrigatório.");
        if (policy == null) throw new IllegalArgumentException("policy é obrigatória.");

        ReplaySemanticContent fields = ReplaySemanticContent.from(candidate);

        List<String> lines = new ArrayList<>();
        BoundedText title = bounded(fields.title(), policy.maxTitleChars());
        lines.add("Title: " + title.value());
        lines.add("Type: " + fields.type().name());
        boolean truncated = title.truncated();

        LimitedList tags = limited(fields.tags(), policy.maxTags());
        LimitedList stack = limited(fields.stack(), policy.maxStack());
        truncated |= tags.truncated() || stack.truncated();
        appendList(lines, "Tags", tags.values());
        appendList(lines, "Stack", stack.values());

        BoundedText problem = bounded(fields.problem(), policy.maxProblemChars());
        BoundedText context = bounded(fields.context(), policy.maxContextChars());
        BoundedText solution = bounded(fields.solution(), policy.maxSolutionChars());
        truncated |= problem.truncated() || context.truncated() || solution.truncated();
        appendText(lines, "Problem", problem.value());
        appendText(lines, "Context", context.value());
        appendText(lines, "Solution", solution.value());

        return new ReplayCompactRepresentation(candidate.replayId(), candidate.retrievalRank(),
                candidate.hybridScore(), candidate.lexicalRank(), candidate.vectorRank(),
                String.join("\n", lines), truncated);
    }

    public List<ReplayCompactRepresentation> compactAll(ReplayRetrievalResult result,
            ReplayCompactRepresentationPolicy policy) {
        if (result == null) throw new IllegalArgumentException("result é obrigatório.");
        if (policy == null) throw new IllegalArgumentException("policy é obrigatória.");
        return result.candidates().stream().map(candidate -> compact(candidate, policy)).toList();
    }

    private LimitedList limited(List<String> source, int maxItems) {
        if (source == null || source.isEmpty()) return new LimitedList(List.of(), false);
        boolean truncated = source.size() > maxItems;
        List<String> values = source.stream().limit(maxItems).map(this::trimToNull)
                .filter(value -> value != null).toList();
        return new LimitedList(values, truncated);
    }

    private void appendList(List<String> lines, String label, List<String> values) {
        if (!values.isEmpty()) lines.add(label + ": " + String.join(", ", values));
    }

    private void appendText(List<String> lines, String label, String value) {
        if (value != null) lines.add(label + ": " + value);
    }

    private BoundedText bounded(String source, int maxCodePoints) {
        String value = trimToNull(source);
        if (value == null) return new BoundedText(null, false);
        int length = value.codePointCount(0, value.length());
        if (length <= maxCodePoints) return new BoundedText(value, false);
        int end = value.offsetByCodePoints(0, maxCodePoints - 1);
        return new BoundedText(value.substring(0, end) + "…", true);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record LimitedList(List<String> values, boolean truncated) { }

    private record BoundedText(String value, boolean truncated) { }
}
