package com.no8do.api.replay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Pure deterministic rendering of the sources already approved by a context package. */
public final class ReplayContextRenderer {
    public ReplayRenderedContext render(ReplayContextPackage contextPackage) {
        if (contextPackage == null) throw new IllegalArgumentException("contextPackage é obrigatório.");

        Map<UUID, ReplayBudgetedContextEntry> compactById = indexCompact(contextPackage.compactSources());
        Map<UUID, ReplayFullSourceEntry> fullById = indexFull(contextPackage.fullSources());
        Set<UUID> renderedIds = new HashSet<>();
        List<ReplayRenderedSource> sources = new ArrayList<>(contextPackage.sourceManifest().size());
        StringBuilder content = new StringBuilder();
        if (!contextPackage.sourceManifest().isEmpty()) content.append("<retrieved-context role=\"reference-data\">\n");

        for (ReplayContextSource manifestSource : contextPackage.sourceManifest()) {
            if (!renderedIds.add(manifestSource.replayId())) {
                throw new IllegalArgumentException("sourceManifest contém Replay duplicado.");
            }
            String sourceContent = switch (manifestSource.representationType()) {
                case COMPACT -> {
                    ReplayBudgetedContextEntry entry = compactById.remove(manifestSource.replayId());
                    if (entry == null) throw new IllegalArgumentException("sourceManifest COMPACT sem entrada correspondente.");
                    requireMatch(manifestSource, entry.representation().replayId(), entry.representation().retrievalRank(),
                            entry.representation().hybridScore(), entry.representation().lexicalRank(),
                            entry.representation().vectorRank(), ReplayContextRepresentationType.COMPACT);
                    yield entry.representation().content();
                }
                case FULL -> {
                    ReplayFullSourceEntry entry = fullById.remove(manifestSource.replayId());
                    if (entry == null) throw new IllegalArgumentException("sourceManifest FULL sem entrada correspondente.");
                    requireMatch(manifestSource, entry.representation().replayId(), entry.representation().retrievalRank(),
                            entry.representation().hybridScore(), entry.representation().lexicalRank(),
                            entry.representation().vectorRank(), ReplayContextRepresentationType.FULL);
                    yield entry.representation().content();
                }
            };

            String label = "S" + (sources.size() + 1);
            sources.add(new ReplayRenderedSource(label, manifestSource.replayId(), manifestSource.retrievalRank(),
                    manifestSource.representationType(), manifestSource.hybridScore(),
                    manifestSource.lexicalRank(), manifestSource.vectorRank()));
            appendSource(content, label, manifestSource.representationType(), sourceContent, sources.size() > 1);
        }

        if (!compactById.isEmpty() || !fullById.isEmpty()) {
            throw new IllegalArgumentException("Há fonte sem entrada correspondente no sourceManifest.");
        }
        if (!sources.isEmpty()) content.append("\n</retrieved-context>");
        return new ReplayRenderedContext(contextPackage.query(), content.toString(), sources,
                contextPackage.tokenAccounting());
    }

    private Map<UUID, ReplayBudgetedContextEntry> indexCompact(List<ReplayBudgetedContextEntry> entries) {
        Map<UUID, ReplayBudgetedContextEntry> result = new HashMap<>();
        for (ReplayBudgetedContextEntry entry : entries) {
            if (result.putIfAbsent(entry.representation().replayId(), entry) != null) {
                throw new IllegalArgumentException("compactSources contém Replay duplicado.");
            }
        }
        return result;
    }

    private Map<UUID, ReplayFullSourceEntry> indexFull(List<ReplayFullSourceEntry> entries) {
        Map<UUID, ReplayFullSourceEntry> result = new HashMap<>();
        for (ReplayFullSourceEntry entry : entries) {
            if (result.putIfAbsent(entry.representation().replayId(), entry) != null) {
                throw new IllegalArgumentException("fullSources contém Replay duplicado.");
            }
        }
        return result;
    }

    private void requireMatch(ReplayContextSource manifest, UUID replayId, int retrievalRank, double hybridScore,
            Integer lexicalRank, Integer vectorRank, ReplayContextRepresentationType representationType) {
        if (!manifest.replayId().equals(replayId) || manifest.retrievalRank() != retrievalRank
                || manifest.representationType() != representationType
                || Double.compare(manifest.hybridScore(), hybridScore) != 0
                || !Objects.equals(manifest.lexicalRank(), lexicalRank)
                || !Objects.equals(manifest.vectorRank(), vectorRank)) {
            throw new IllegalArgumentException("Fonte diverge do sourceManifest.");
        }
    }

    private void appendSource(StringBuilder output, String label, ReplayContextRepresentationType type,
            String sourceContent, boolean separateFromPrevious) {
        if (separateFromPrevious) output.append('\n');
        output.append("<source label=\"").append(label).append("\" type=\"").append(type)
                .append("\" utf16-length=\"").append(sourceContent.length()).append("\">\n")
                .append(sourceContent).append("\n</source>");
    }
}
