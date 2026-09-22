package com.no8do.api.replay;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pure projection of retrieval candidates into bounded, provider-agnostic text. */
public final class ReplayCompactRepresentationFactory {
    public ReplayCompactRepresentation compact(ReplayRetrievalCandidate candidate,
            ReplayCompactRepresentationPolicy policy) {
        if (candidate == null) throw new IllegalArgumentException("candidate é obrigatório.");
        if (policy == null) throw new IllegalArgumentException("policy é obrigatória.");

        ReplayResponse response = candidate.replay();
        ReplayVersion version = candidate.replayVersion();
        UUID responseId = response == null ? null : response.id();
        UUID versionId = version == null ? null : snapshotReplayId(version);
        requireCandidateIdentity(candidate.replayId(), responseId);
        requireCandidateIdentity(candidate.replayId(), versionId);

        SemanticFields fields = version != null ? fromVersion(version) : fromResponse(response);
        if (fields.title() == null || fields.title().isBlank() || fields.type() == null) {
            throw new IllegalArgumentException("Fonte do Replay sem conteúdo semântico suficiente.");
        }

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

    private UUID snapshotReplayId(ReplayVersion version) {
        if (version.getReplay() == null || version.getReplay().getId() == null) {
            throw new IllegalArgumentException("Snapshot sem identidade de Replay.");
        }
        return version.getReplay().getId();
    }

    private void requireCandidateIdentity(UUID candidateId, UUID sourceId) {
        if (sourceId != null && !candidateId.equals(sourceId)) {
            throw new IllegalArgumentException("Identidade da fonte diverge do replayId do candidato.");
        }
    }

    private SemanticFields fromResponse(ReplayResponse response) {
        if (response == null) throw new IllegalArgumentException("Candidato sem fonte de conteúdo.");
        return new SemanticFields(response.title(), response.type(), response.tags(), response.stack(),
                response.problem(), response.context(), response.solution());
    }

    private SemanticFields fromVersion(ReplayVersion version) {
        return new SemanticFields(version.getTitle(), version.getType(), arrayList(version.getTags()),
                arrayList(version.getStack()), version.getProblem(), version.getContext(), version.getSolution());
    }

    private List<String> arrayList(String[] values) {
        return values == null ? List.of() : java.util.Arrays.asList(values);
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

    private record SemanticFields(String title, ReplayType type, List<String> tags, List<String> stack,
            String problem, String context, String solution) { }

    private record LimitedList(List<String> values, boolean truncated) { }

    private record BoundedText(String value, boolean truncated) { }
}
