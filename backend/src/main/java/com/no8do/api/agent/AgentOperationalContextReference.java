package com.no8do.api.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "agent_session_context_references")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentOperationalContextReference {
    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private int ordinal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentContextReferenceKind kind;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(name = "reference_key", nullable = false, length = 128)
    private String referenceKey;

    AgentOperationalContextReference(UUID sessionId, int ordinal, AgentContextReferenceKind kind,
            String provider, String referenceKey) {
        this.id = UUID.randomUUID();
        this.sessionId = sessionId;
        this.ordinal = ordinal;
        this.kind = kind;
        this.provider = provider;
        this.referenceKey = referenceKey;
    }

    void update(int ordinal, AgentContextReferenceKind kind, String provider, String referenceKey) {
        this.ordinal = ordinal;
        this.kind = kind;
        this.provider = provider;
        this.referenceKey = referenceKey;
    }
}
