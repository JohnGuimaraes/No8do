package com.no8do.api.replay.embedding;

import com.no8do.api.replay.ReplayType;
import java.util.List;

/** Campos que definem o conteúdo semântico de um Replay ainda não persistido. */
public record ReplaySemanticDuplicateQuery(String title, ReplayType type, List<String> tags, List<String> stack,
        String problem, String context, String solution) {
    public ReplaySemanticDuplicateQuery {
        tags = tags == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(tags));
        stack = stack == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(stack));
    }
}
