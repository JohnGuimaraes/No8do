package com.no8do.api.agent;

import java.util.List;

record AgentOperationalContextSignal(Repository repository, String branch, String workingDirectory,
        List<Reference> references) {
    AgentOperationalContextSignal {
        references = List.copyOf(references);
    }

    record Repository(String vcs, String provider, String host, String namespace, String name) {}

    record Reference(AgentContextReferenceKind kind, String provider, String key) {}
}
