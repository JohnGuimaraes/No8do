package com.no8do.api.replay.embedding;

import com.no8do.api.replay.ReplayVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ReplayCanonicalizer {

    public CanonicalReplayContent canonicalize(ReplayVersion version) {
        Objects.requireNonNull(version, "ReplayVersion é obrigatória.");

        List<String> sections = new ArrayList<>();
        sections.add("Título: " + requiredText("Título", version.getTitle()));
        if (version.getType() == null) {
            throw new IllegalArgumentException("Tipo de ReplayVersion é obrigatório.");
        }
        sections.add("Tipo: " + version.getType().name());
        appendTerms(sections, "Tags", version.getTags());
        appendTerms(sections, "Stack", version.getStack());
        appendSection(sections, "Problema", version.getProblem());
        appendSection(sections, "Contexto", version.getContext());
        appendSection(sections, "Solução", version.getSolution());

        return new CanonicalReplayContent(String.join("\n", sections));
    }

    private void appendTerms(List<String> sections, String label, String[] terms) {
        if (terms == null) {
            return;
        }
        List<String> normalizedTerms = new ArrayList<>();
        for (String term : terms) {
            String normalized = optionalText(term);
            if (normalized != null) {
                normalizedTerms.add(normalized);
            }
        }
        if (!normalizedTerms.isEmpty()) {
            sections.add(label + ": " + String.join(" | ", normalizedTerms));
        }
    }

    private void appendSection(List<String> sections, String label, String value) {
        String normalized = optionalText(value);
        if (normalized != null) {
            sections.add(label + ":\n" + normalized);
        }
    }

    private String requiredText(String label, String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " de ReplayVersion é obrigatório.");
        }
        return normalized;
    }

    private String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
