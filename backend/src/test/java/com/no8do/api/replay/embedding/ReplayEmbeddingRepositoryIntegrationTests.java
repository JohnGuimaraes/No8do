package com.no8do.api.replay.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.no8do.api.replay.*;
import com.no8do.api.user.*;
import com.no8do.api.workspace.*;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PGobject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.NestedExceptionUtils;

@SpringBootTest
class ReplayEmbeddingRepositoryIntegrationTests {
 @Autowired ReplayEmbeddingRepository embeddings; @Autowired ReplayVersionRepository versions; @Autowired ReplayRepository replays; @Autowired WorkspaceRepository workspaces; @Autowired UserRepository users; @Autowired EntityManager entityManager; @Autowired JdbcTemplate jdbcTemplate;
 @Test void persistsAndReadsVector() { ReplayVersion v=version(); var d=new EmbeddingProviderDescriptor("test-provider","test-model",2); ReplayEmbedding saved=embeddings.saveAndFlush(new ReplayEmbedding(v.getWorkspace().getId(),v.getReplay().getId(),v.getId(),d,"a".repeat(64),new EmbeddingResult(new float[]{.1f,.2f}))); entityManager.clear(); ReplayEmbedding read=embeddings.findById(saved.getId()).orElseThrow(); assertThat(read.getWorkspaceId()).isEqualTo(v.getWorkspace().getId()); assertThat(read.getReplayId()).isEqualTo(v.getReplay().getId()); assertThat(read.getReplayVersionId()).isEqualTo(v.getId()); assertThat(read.getEmbedding()).containsExactly(.1f,.2f); assertThat(read.getCreatedAt()).isNotNull(); }
 @Test void databaseRejectsVectorDimensionMismatchThroughJdbc() throws Exception { ReplayVersion v=version(); assertThatThrownBy(() -> jdbcTemplate.update("""
     insert into replay_embeddings (id, workspace_id, replay_id, replay_version_id, provider, model, dimensions, content_hash, embedding, created_at)
     values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
     """, UUID.randomUUID(), v.getWorkspace().getId(), v.getReplay().getId(), v.getId(), "test-provider", "test-model", 3, "b".repeat(64), vector("[0.1,0.2]"), OffsetDateTime.now()))
     .isInstanceOf(DataIntegrityViolationException.class)
     .satisfies(this::assertVectorDimensionConstraint);
 }
 @Test void databaseRejectsDuplicateKey() { ReplayVersion v=version(); var d=new EmbeddingProviderDescriptor("test-provider","test-model",2); embeddings.saveAndFlush(new ReplayEmbedding(v.getWorkspace().getId(),v.getReplay().getId(),v.getId(),d,"c".repeat(64),new EmbeddingResult(new float[]{.1f,.2f}))); assertThatThrownBy(()->embeddings.saveAndFlush(new ReplayEmbedding(v.getWorkspace().getId(),v.getReplay().getId(),v.getId(),d,"d".repeat(64),new EmbeddingResult(new float[]{.3f,.4f})))).isInstanceOf(DataIntegrityViolationException.class).satisfies(error -> assertConstraint(error, "23505", "uq_replay_embeddings_version_provider_model_dimensions")); }
 private void assertVectorDimensionConstraint(Throwable error) { assertConstraint(error, "23514", "chk_replay_embeddings_vector_dimensions"); }
 private PGobject vector(String value) throws Exception { PGobject vector = new PGobject(); vector.setType("vector"); vector.setValue(value); return vector; }
 private void assertConstraint(Throwable error, String sqlState, String constraint) { Throwable cause = NestedExceptionUtils.getMostSpecificCause(error); assertThat(cause).isInstanceOf(PSQLException.class); PSQLException postgres = (PSQLException) cause; assertThat(postgres.getSQLState()).isEqualTo(sqlState); assertThat(postgres.getServerErrorMessage().getConstraint()).isEqualTo(constraint); }
 private ReplayVersion version(){ User u=users.saveAndFlush(new User("U",UUID.randomUUID()+"@e.com","h")); Workspace w=workspaces.saveAndFlush(new Workspace("W"+UUID.randomUUID())); Replay r=replays.saveAndFlush(new Replay(w,null,"T",ReplayType.FIX,u)); return versions.saveAndFlush(new ReplayVersion(r,u)); }
}
