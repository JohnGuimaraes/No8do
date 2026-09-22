package com.no8do.api.replay.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.no8do.api.replay.Replay;
import com.no8do.api.replay.ReplayStatus;
import com.no8do.api.replay.ReplayType;
import com.no8do.api.replay.ReplayVersion;
import com.no8do.api.user.User;
import com.no8do.api.workspace.Workspace;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplaySemanticDuplicateCanonicalizationTests {
    @Test void queryUsesExactlyTheCanonicalRepresentationOfAnEquivalentVersion() {
        Workspace workspace = new Workspace("Workspace"); Replay replay = new Replay(workspace, null, " Title ", ReplayType.PATTERN, new User("User", "duplicate@example.com", "hash"));
        replay.setTags(new String[] {" alpha ", "beta"}); replay.setStack(new String[] {" Java "}); replay.setProblem(" problem\r\n details "); replay.setContext(" context "); replay.setSolution(" solution "); replay.setStatus(ReplayStatus.VALIDATED);
        ReplayVersion version = new ReplayVersion(replay, null);
        ReplaySemanticDuplicateQuery query = new ReplaySemanticDuplicateQuery(" Title ", ReplayType.PATTERN, List.of(" alpha ", "beta"), List.of(" Java "), " problem\r\n details ", " context ", " solution ");
        ReplayCanonicalizer canonicalizer = new ReplayCanonicalizer();
        assertThat(canonicalizer.canonicalize(query)).isEqualTo(canonicalizer.canonicalize(version));
    }
}
