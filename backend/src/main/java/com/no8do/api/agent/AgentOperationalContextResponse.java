package com.no8do.api.agent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentOperationalContextResponse(UUID sessionId, long version, Signal signal,
        ResolutionPair resolution, Instant updatedAt) {
    static AgentOperationalContextResponse from(AgentOperationalContext context) {
        Repository repository = context.getRepositoryName() == null ? null : new Repository(
                context.getRepositoryVcs(), context.getRepositoryProvider(), context.getRepositoryHost(),
                context.getRepositoryNamespace(), context.getRepositoryName());
        List<Reference> references = context.getReferences().stream()
                .map(value -> new Reference(value.getKind(), value.getProvider(), value.getReferenceKey())).toList();
        UUID projectId = context.getResolvedProjectId();
        UUID workItemId = context.getResolvedWorkItemId();
        OperationalContextResolutionStatus projectStatus = normalizeStatus(
                context.getProjectResolutionStatus(), projectId);
        OperationalContextResolutionStatus workItemStatus = normalizeStatus(
                context.getWorkItemResolutionStatus(), workItemId);
        Resolution project = new Resolution(projectStatus == OperationalContextResolutionStatus.RESOLVED ? projectId : null,
                projectStatus, projectId == null ? null : context.getProjectConfidence());
        Resolution workItem = new Resolution(workItemStatus == OperationalContextResolutionStatus.RESOLVED ? workItemId : null,
                workItemStatus, workItemId == null ? null : context.getWorkItemConfidence());
        return new AgentOperationalContextResponse(context.getSessionId(), context.getVersion(),
                new Signal(repository, context.getBranch(), context.getWorkingDirectory(), references),
                new ResolutionPair(project, workItem), context.getUpdatedAt());
    }

    private static OperationalContextResolutionStatus normalizeStatus(
            OperationalContextResolutionStatus status, UUID id) {
        return status == OperationalContextResolutionStatus.RESOLVED && id == null
                ? OperationalContextResolutionStatus.UNRESOLVED : status;
    }

    public record Signal(Repository repository, String branch, String workingDirectory, List<Reference> references) {}
    public record Repository(String vcs, String provider, String host, String namespace, String name) {}
    public record Reference(AgentContextReferenceKind kind, String provider, String key) {}
    public record Resolution(UUID id, OperationalContextResolutionStatus status,
            OperationalContextConfidence confidence) {}
    public record ResolutionPair(Resolution project, Resolution workItem) {}
}
