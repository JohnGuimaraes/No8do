package com.no8do.api.replay;

import java.util.List;

/** Immutable textual rendering and source map for a context package. */
public record ReplayRenderedContext(String query, String content, List<ReplayRenderedSource> sources,
        ReplayContextTokenAccounting tokenAccounting) {
    public ReplayRenderedContext {
        if (query == null) throw new IllegalArgumentException("query é obrigatória.");
        if (content == null) throw new IllegalArgumentException("content é obrigatório.");
        sources = List.copyOf(sources);
        if (tokenAccounting == null) throw new IllegalArgumentException("tokenAccounting é obrigatório.");
    }
}
