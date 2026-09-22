package com.no8do.api.replay;

import static org.assertj.core.api.Assertions.assertThat;

import com.no8do.api.replay.embedding.EmbeddingProvider;
import com.no8do.api.replay.embedding.EmbeddingProviderDescriptor;
import com.no8do.api.replay.embedding.EmbeddingResult;
import com.no8do.api.replay.embedding.ReplayEmbedding;
import com.no8do.api.replay.embedding.ReplayEmbeddingRepository;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReplayRetrievalEvaluationIntegrationTests {
    private static final EmbeddingProviderDescriptor DESCRIPTOR = new EmbeddingProviderDescriptor("evaluation-test", "deterministic", 2);
    private static final EmbeddingProvider PROVIDER = new EmbeddingProvider() {
        @Override public EmbeddingProviderDescriptor descriptor() { return DESCRIPTOR; }
        @Override public EmbeddingResult embed(String text) { return new EmbeddingResult(new float[] {1F, 0F}); }
    };

    @Autowired ReplayRetrievalEvaluationService evaluationService;
    @Autowired ReplayRepository replayRepository;
    @Autowired ReplayVersionRepository replayVersionRepository;
    @Autowired ReplayEmbeddingRepository embeddingRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired UserRepository userRepository;

    @Test void evaluatesRealLexicalVectorAndHybridSearchWithDeterministicProvider() {
        User user = userRepository.saveAndFlush(new User("Evaluation", UUID.randomUUID() + "@example.com", "hash"));
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("Evaluation " + UUID.randomUUID()));
        workspaceMemberRepository.saveAndFlush(new WorkspaceMember(workspace, user, WorkspaceRole.MEMBER));
        Replay relevant = replay(workspace, user, "semantic vector retrieval");
        Replay other = replay(workspace, user, "semantic vector alternative");
        embed(relevant, new float[] {1F, 0F}); embed(other, new float[] {0F, 1F});

        ReplayRetrievalEvaluationReport report = evaluationService.evaluate(workspace.getId(), user.getId(),
                List.of(new ReplayRetrievalEvaluationCase("semantic-vector", "semantic vector", Set.of(relevant.getId()))), PROVIDER, 2);

        assertThat(report.results()).hasSize(3);
        assertThat(report.results().values()).allSatisfy(result -> {
            assertThat(result.meanPrecisionAtK()).isBetween(0D, 1D);
            assertThat(result.meanRecallAtK()).isBetween(0D, 1D);
            assertThat(result.meanMrrAtK()).isBetween(0D, 1D);
            assertThat(result.meanNdcgAtK()).isBetween(0D, 1D);
        });
        assertThat(report.results().get(ReplayRetrievalStrategy.VECTOR).meanRecallAtK()).isEqualTo(1D);
    }

    private Replay replay(Workspace workspace, User user, String title) {
        Replay replay = replayRepository.saveAndFlush(new Replay(workspace, null, title, ReplayType.PATTERN, user));
        replay.setStatus(ReplayStatus.VALIDATED);
        replay = replayRepository.saveAndFlush(replay);
        replayVersionRepository.saveAndFlush(new ReplayVersion(replay, user));
        return replay;
    }

    private void embed(Replay replay, float[] vector) {
        ReplayVersion version = replayVersionRepository.findByReplayIdOrderByVersionDesc(replay.getId()).getFirst();
        embeddingRepository.saveAndFlush(new ReplayEmbedding(replay.getWorkspace().getId(), replay.getId(), version.getId(), DESCRIPTOR,
                UUID.randomUUID().toString().replace("-", "") + "a".repeat(32), new EmbeddingResult(vector)));
    }
}
