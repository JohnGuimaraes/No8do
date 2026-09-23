package com.no8do.api.replay;

/** Concise, auditable evidence supporting a Replay's VALIDATED status. */
public record ReplayValidationEvidence(String summary, String method, String reference) {
    public boolean satisfiesValidationRequirements() {
        return summary != null && !summary.isBlank() && summary.length() <= 1000
                && method != null && !method.isBlank() && method.length() <= 250
                && (reference == null || (!reference.isBlank() && reference.length() <= 2000));
    }

    public ReplayValidationEvidence normalized() {
        return new ReplayValidationEvidence(summary == null ? null : summary.trim(),
                method == null ? null : method.trim(), reference == null ? null : reference.trim());
    }
}
