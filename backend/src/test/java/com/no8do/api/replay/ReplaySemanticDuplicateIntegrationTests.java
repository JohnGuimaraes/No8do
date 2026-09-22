package com.no8do.api.replay;

import static org.assertj.core.api.Assertions.assertThat;

import com.no8do.api.replay.embedding.EmbeddingProvider;
import com.no8do.api.replay.embedding.EmbeddingProviderDescriptor;
import com.no8do.api.replay.embedding.EmbeddingResult;
import com.no8do.api.replay.embedding.ReplayEmbedding;
import com.no8do.api.replay.embedding.ReplayEmbeddingRepository;
import com.no8do.api.replay.embedding.ReplaySemanticDuplicateQuery;
import com.no8do.api.user.User;
import com.no8do.api.user.UserRepository;
import com.no8do.api.workspace.Workspace;
import com.no8do.api.workspace.WorkspaceMember;
import com.no8do.api.workspace.WorkspaceMemberRepository;
import com.no8do.api.workspace.WorkspaceRepository;
import com.no8do.api.workspace.WorkspaceRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReplaySemanticDuplicateIntegrationTests {
    private static final EmbeddingProviderDescriptor DESCRIPTOR = new EmbeddingProviderDescriptor("duplicate-test", "deterministic", 2);
    private static final EmbeddingProvider PROVIDER = new EmbeddingProvider() {
        @Override public EmbeddingProviderDescriptor descriptor() { return DESCRIPTOR; }
        @Override public EmbeddingResult embed(String text) { return new EmbeddingResult(new float[] {1F, 0F}); }
    };
    @Autowired ReplaySemanticDuplicateService service;
    @Autowired ReplayRepository replays;
    @Autowired ReplayVersionRepository versions;
    @Autowired ReplayEmbeddingRepository embeddings;
    @Autowired WorkspaceRepository workspaces;
    @Autowired WorkspaceMemberRepository members;
    @Autowired UserRepository users;

    @Test void filtersControlledPgvectorCandidatesByWorkspaceCurrentVersionThresholdAndSelf() {
        User user = users.saveAndFlush(new User("Duplicate", UUID.randomUUID() + "@example.com", "hash"));
        Workspace workspace = workspaces.saveAndFlush(new Workspace("Duplicate " + UUID.randomUUID()));
        Workspace otherWorkspace = workspaces.saveAndFlush(new Workspace("Other " + UUID.randomUUID()));
        members.saveAndFlush(new WorkspaceMember(workspace, user, WorkspaceRole.MEMBER));
        Replay self = replay(workspace, user, "Semantic content"); Replay a = replay(workspace, user, "Semantic A"); Replay b = replay(workspace, user, "Semantic B"); Replay far = replay(workspace, user, "Semantic far");
        Replay outsider = replay(otherWorkspace, user, "Semantic outsider");
        embed(current(self), new float[] {1F, 0F}); embed(current(a), new float[] {.99F, .14F}); embed(current(b), new float[] {.92F, .39F}); embed(current(far), new float[] {0F, 1F}); embed(current(outsider), new float[] {1F, 0F});
        ReplayVersion historical = current(a); a.incrementVersion(); a = replays.saveAndFlush(a); ReplayVersion currentA = versions.saveAndFlush(new ReplayVersion(a, user)); embed(currentA, new float[] {.99F, .14F});

        List<ReplaySemanticDuplicateCandidate> result = service.findDuplicates(workspace.getId(), user.getId(), query(), self.getId(), PROVIDER, 5, .1D);

        assertThat(result).extracting(ReplaySemanticDuplicateCandidate::replayId).containsExactly(a.getId(), b.getId());
        assertThat(result).extracting(ReplaySemanticDuplicateCandidate::distance).isSorted();
        assertThat(result).extracting(ReplaySemanticDuplicateCandidate::replayId).doesNotContain(self.getId(), far.getId(), outsider.getId());
    }

    private Replay replay(Workspace workspace, User user, String title) { Replay replay = replays.saveAndFlush(new Replay(workspace, null, title, ReplayType.PATTERN, user)); replay.setStatus(ReplayStatus.VALIDATED); return replays.saveAndFlush(replay); }
    private ReplayVersion current(Replay replay) { return versions.findByReplayIdOrderByVersionDesc(replay.getId()).isEmpty() ? versions.saveAndFlush(new ReplayVersion(replay, replay.getCreatedBy())) : versions.findByReplayIdOrderByVersionDesc(replay.getId()).getFirst(); }
    private void embed(ReplayVersion version, float[] vector) { embeddings.saveAndFlush(new ReplayEmbedding(version.getWorkspace().getId(), version.getReplay().getId(), version.getId(), DESCRIPTOR, UUID.randomUUID().toString().replace("-", "") + "a".repeat(32), new EmbeddingResult(vector))); }
    private ReplaySemanticDuplicateQuery query() { return new ReplaySemanticDuplicateQuery("Semantic content", ReplayType.PATTERN, List.of(), List.of(), null, null, null); }
}
