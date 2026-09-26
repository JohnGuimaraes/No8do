package com.no8do.api.agent;

import com.no8do.api.user.User;
import com.no8do.api.workspace.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "agents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Agent {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, updatable = false)
    private Workspace workspace;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "provider_descriptor", columnDefinition = "text")
    private String providerDescriptor;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private AgentLifecycleStatus lifecycleStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", updatable = false)
    private User createdByUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    Agent(Workspace workspace, String name, String description, String providerDescriptor, User createdByUser) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.name = requireName(name);
        this.description = description;
        this.providerDescriptor = providerDescriptor;
        this.lifecycleStatus = AgentLifecycleStatus.ACTIVE;
        this.createdByUser = Objects.requireNonNull(createdByUser, "createdByUser");
    }

    boolean updateDetails(String name, String description, String providerDescriptor) {
        String validatedName = requireName(name);
        boolean changed = !Objects.equals(this.name, validatedName)
                || !Objects.equals(this.description, description)
                || !Objects.equals(this.providerDescriptor, providerDescriptor);
        if (changed) {
            this.name = validatedName;
            this.description = description;
            this.providerDescriptor = providerDescriptor;
        }
        return changed;
    }

    boolean changeLifecycleStatus(AgentLifecycleStatus nextStatus) {
        Objects.requireNonNull(nextStatus, "nextStatus");
        if (lifecycleStatus == nextStatus) return false;
        boolean allowed = switch (lifecycleStatus) {
            case ACTIVE -> nextStatus == AgentLifecycleStatus.DISABLED || nextStatus == AgentLifecycleStatus.ARCHIVED;
            case DISABLED -> nextStatus == AgentLifecycleStatus.ACTIVE || nextStatus == AgentLifecycleStatus.ARCHIVED;
            case ARCHIVED -> false;
        };
        if (!allowed) throw new IllegalStateException("Agent lifecycle transition is not allowed");
        lifecycleStatus = nextStatus;
        return true;
    }

    private static String requireName(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Agent name is required");
        String normalized = value.trim();
        if (normalized.length() > 160) throw new IllegalArgumentException("Agent name is too long");
        return normalized;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (lifecycleStatus == null) lifecycleStatus = AgentLifecycleStatus.ACTIVE;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
