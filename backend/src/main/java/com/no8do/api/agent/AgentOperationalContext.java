package com.no8do.api.agent;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "agent_session_operational_contexts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentOperationalContext {
    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", referencedColumnName = "id", insertable = false, updatable = false)
    private AgentSession session;

    @Column(name = "repository_vcs", length = 16)
    private String repositoryVcs;

    @Column(name = "repository_provider", length = 64)
    private String repositoryProvider;

    @Column(name = "repository_host", length = 253)
    private String repositoryHost;

    @Column(name = "repository_namespace", length = 512)
    private String repositoryNamespace;

    @Column(name = "repository_name", length = 255)
    private String repositoryName;

    @Column(length = 255)
    private String branch;

    @Column(name = "working_directory", length = 1024)
    private String workingDirectory;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "signal_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String signalHash;

    @Version
    @Column(nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_resolution_status", nullable = false, length = 20)
    private OperationalContextResolutionStatus projectResolutionStatus;

    @Column(name = "resolved_project_id")
    private UUID resolvedProjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_confidence", length = 12)
    private OperationalContextConfidence projectConfidence;

    @Column(name = "project_resolution_repository_id", length = 20)
    private String projectResolutionRepositoryId;

    @Column(name = "project_resolution_evidence", length = 32)
    private String projectResolutionEvidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_item_resolution_status", nullable = false, length = 20)
    private OperationalContextResolutionStatus workItemResolutionStatus;

    @Column(name = "resolved_work_item_id")
    private UUID resolvedWorkItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_item_confidence", length = 12)
    private OperationalContextConfidence workItemConfidence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "session_id", referencedColumnName = "session_id", insertable = false, updatable = false)
    @OrderBy("ordinal ASC")
    private List<AgentOperationalContextReference> references = new ArrayList<>();

    AgentOperationalContext(UUID sessionId, AgentOperationalContextSignal signal, String signalHash) {
        this.sessionId = sessionId;
        applySignal(signal, signalHash);
        this.projectResolutionStatus = OperationalContextResolutionStatus.UNRESOLVED;
        this.workItemResolutionStatus = OperationalContextResolutionStatus.UNRESOLVED;
    }

    void applySignal(AgentOperationalContextSignal signal, String signalHash) {
        repositoryVcs = signal.repository() == null ? null : signal.repository().vcs();
        repositoryProvider = signal.repository() == null ? null : signal.repository().provider();
        repositoryHost = signal.repository() == null ? null : signal.repository().host();
        repositoryNamespace = signal.repository() == null ? null : signal.repository().namespace();
        repositoryName = signal.repository() == null ? null : signal.repository().name();
        branch = signal.branch();
        workingDirectory = signal.workingDirectory();
        this.signalHash = signalHash;
        for (int i = 0; i < signal.references().size(); i++) {
            AgentOperationalContextSignal.Reference reference = signal.references().get(i);
            if (i < references.size()) {
                references.get(i).update(i, reference.kind(), reference.provider(), reference.key());
            } else {
                references.add(new AgentOperationalContextReference(sessionId, i,
                        reference.kind(), reference.provider(), reference.key()));
            }
        }
        while (references.size() > signal.references().size()) references.removeLast();
    }

    boolean referencesMatch(List<AgentOperationalContextSignal.Reference> values) {
        if (references.size() != values.size()) return false;
        for (int i = 0; i < values.size(); i++) {
            AgentOperationalContextReference current = references.get(i);
            AgentOperationalContextSignal.Reference next = values.get(i);
            if (current.getOrdinal() != i || current.getKind() != next.kind()
                    || !current.getProvider().equals(next.provider())
                    || !current.getReferenceKey().equals(next.key())) return false;
        }
        return true;
    }

    void parkReferenceIdentities() {
        for (AgentOperationalContextReference reference : references) {
            reference.update(reference.getOrdinal(), reference.getKind(), reference.getProvider(),
                    "pending-" + reference.getId());
        }
    }

    void applyProjectResolution(OperationalContextResolutionStatus status, UUID projectId,
            OperationalContextConfidence confidence, String repositoryId, String evidence) {
        this.projectResolutionStatus = status;
        this.resolvedProjectId = projectId;
        this.projectConfidence = confidence;
        this.projectResolutionRepositoryId = repositoryId;
        this.projectResolutionEvidence = evidence;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
