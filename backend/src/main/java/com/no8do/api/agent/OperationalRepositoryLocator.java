package com.no8do.api.agent;

public record OperationalRepositoryLocator(String vcs, String provider, String host, String namespace, String name) {
    static OperationalRepositoryLocator from(AgentOperationalContextSignal.Repository repository) {
        return repository == null ? null : new OperationalRepositoryLocator(repository.vcs(), repository.provider(),
                repository.host(), repository.namespace(), repository.name());
    }
}
