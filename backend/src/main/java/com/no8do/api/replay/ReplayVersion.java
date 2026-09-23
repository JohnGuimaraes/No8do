package com.no8do.api.replay;

import com.no8do.api.user.User;
import com.no8do.api.workspace.Workspace;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel; import lombok.Getter; import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes;

@Entity @Table(name = "replay_versions") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplayVersion {
 @Id private UUID id; @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name="replay_id", nullable=false) private Replay replay;
 @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name="workspace_id", nullable=false) private Workspace workspace;
 @Column(nullable=false) private int version; @Column(nullable=false,length=180) private String title;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private ReplayType type; @Column(columnDefinition="text") private String problem; @Column(columnDefinition="text") private String solution; @Column(columnDefinition="text") private String context;
 @JdbcTypeCode(SqlTypes.ARRAY) @Column(nullable=false,columnDefinition="text[]") private String[] tags; @JdbcTypeCode(SqlTypes.ARRAY) @Column(nullable=false,columnDefinition="text[]") private String[] stack;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private ReplayStatus status; @Column(name="project_id") private UUID projectId;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="validation_evidence",columnDefinition="jsonb") private ReplayValidationEvidence validationEvidence;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="changed_by") private User changedBy; @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 public ReplayVersion(Replay replay, User changedBy) { this.replay=replay; this.workspace=replay.getWorkspace(); this.version=replay.getVersion(); this.title=replay.getTitle(); this.type=replay.getType(); this.problem=replay.getProblem(); this.solution=replay.getSolution(); this.context=replay.getContext(); this.tags=replay.getTags().clone(); this.stack=replay.getStack().clone(); this.status=replay.getStatus(); this.validationEvidence=replay.getValidationEvidence(); this.projectId=replay.getProject()==null?null:replay.getProject().getId(); this.changedBy=changedBy; }
 @PrePersist void prePersist(){ if(id==null) id=UUID.randomUUID(); if(createdAt==null) createdAt=Instant.now(); }
}
