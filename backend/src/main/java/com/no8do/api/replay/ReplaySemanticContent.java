package com.no8do.api.replay;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Technical fields shared by compact and full source projections. */
record ReplaySemanticContent(String title, ReplayType type, List<String> tags, List<String> stack,
        String problem, String context, String solution) {
    static ReplaySemanticContent from(ReplayRetrievalCandidate candidate) {
        if (candidate == null) throw new IllegalArgumentException("candidate é obrigatório.");

        ReplayResponse response = candidate.replay();
        ReplayVersion version = candidate.replayVersion();
        UUID responseId = response == null ? null : response.id();
        UUID versionId = version == null ? null : snapshotReplayId(version);
        requireCandidateIdentity(candidate.replayId(), responseId);
        requireCandidateIdentity(candidate.replayId(), versionId);

        ReplaySemanticContent fields = version != null ? fromVersion(version) : fromResponse(response);
        if (fields.title() == null || fields.title().isBlank() || fields.type() == null) {
            throw new IllegalArgumentException("Fonte do Replay sem conteúdo semântico suficiente.");
        }
        return fields;
    }

    private static UUID snapshotReplayId(ReplayVersion version) {
        if (version.getReplay() == null || version.getReplay().getId() == null) {
            throw new IllegalArgumentException("Snapshot sem identidade de Replay.");
        }
        return version.getReplay().getId();
    }

    private static void requireCandidateIdentity(UUID candidateId, UUID sourceId) {
        if (sourceId != null && !candidateId.equals(sourceId)) {
            throw new IllegalArgumentException("Identidade da fonte diverge do replayId do candidato.");
        }
    }

    private static ReplaySemanticContent fromResponse(ReplayResponse response) {
        if (response == null) throw new IllegalArgumentException("Candidato sem fonte de conteúdo.");
        return new ReplaySemanticContent(response.title(), response.type(), response.tags(), response.stack(),
                response.problem(), response.context(), response.solution());
    }

    private static ReplaySemanticContent fromVersion(ReplayVersion version) {
        return new ReplaySemanticContent(version.getTitle(), version.getType(), arrayList(version.getTags()),
                arrayList(version.getStack()), version.getProblem(), version.getContext(), version.getSolution());
    }

    private static List<String> arrayList(String[] values) {
        return values == null ? List.of() : Arrays.asList(values);
    }
}
