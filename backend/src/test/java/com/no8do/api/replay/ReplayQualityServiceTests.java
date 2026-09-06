package com.no8do.api.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.no8do.api.user.User;
import com.no8do.api.workspace.Workspace;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReplayQualityServiceTests {
    @Mock private ReplayRelationRepository relations;
    private ReplayQualityService service;
    private Workspace workspace;

    @BeforeEach void setUp() {
        service = new ReplayQualityService(relations); workspace = new Workspace("Quality");
        ReflectionTestUtils.setField(workspace, "id", UUID.randomUUID());
        when(relations.existsByWorkspaceIdAndTargetReplayIdAndType(any(), any(), any())).thenReturn(false);
    }

    @Test void statusBasesCapsAndLevelsAreApplied() {
        assertThat(service.assess(replay(ReplayStatus.VALIDATED, 0, 0, 0, 1, null)).score()).isEqualTo(45);
        assertThat(service.assess(replay(ReplayStatus.DRAFT, 100, 100, 0, 2, Instant.now())).score()).isEqualTo(49);
        ReplayQualityResponse deprecated = service.assess(replay(ReplayStatus.DEPRECATED, 100, 100, 0, 2, Instant.now()));
        assertThat(deprecated.score()).isEqualTo(20); assertThat(deprecated.level()).isEqualTo(ReplayQualityLevel.LOW);
    }

    @Test void evidenceUsesOutcomeAndSampleSizeWhileUnknownStaysNeutral() {
        int oneSuccess = service.assess(replay(ReplayStatus.VALIDATED, 1, 1, 0, 1, null)).score();
        int manySuccesses = service.assess(replay(ReplayStatus.VALIDATED, 50, 50, 0, 1, null)).score();
        int failures = service.assess(replay(ReplayStatus.VALIDATED, 10, 1, 9, 1, null)).score();
        ReplayQualityResponse unknown = service.assess(replay(ReplayStatus.VALIDATED, 10, 0, 0, 1, null));
        assertThat(manySuccesses).isGreaterThan(oneSuccess); assertThat(failures).isLessThan(manySuccesses);
        assertThat(unknown.successRate()).isNull(); assertThat(unknown.score()).isEqualTo(45);
    }

    @Test void recencyVersionAndSupersedesHaveBoundedEffects() {
        int baseline = service.assess(replay(ReplayStatus.VALIDATED, 0, 0, 0, 1, null)).score();
        Replay evolved = replay(ReplayStatus.VALIDATED, 0, 0, 0, 2, Instant.now());
        assertThat(service.assess(evolved).score()).isEqualTo(baseline + 10);
        when(relations.existsByWorkspaceIdAndTargetReplayIdAndType(any(), any(), any())).thenReturn(true);
        ReplayQualityResponse superseded = service.assess(evolved);
        assertThat(superseded.score()).isEqualTo(baseline - 15); assertThat(superseded.signals()).contains("Substituído por conhecimento mais atual");
        assertThat(superseded.score()).isBetween(0, 100);
    }

    private Replay replay(ReplayStatus status, int usage, int success, int failure, int version, Instant lastUsedAt) {
        Replay replay = new Replay(workspace, null, "Replay", ReplayType.PATTERN, new User("User", "quality@example.com", "hash"));
        ReflectionTestUtils.setField(replay, "id", UUID.randomUUID()); ReflectionTestUtils.setField(replay, "status", status);
        ReflectionTestUtils.setField(replay, "usageCount", usage); ReflectionTestUtils.setField(replay, "successCount", success);
        ReflectionTestUtils.setField(replay, "failureCount", failure); ReflectionTestUtils.setField(replay, "version", version); ReflectionTestUtils.setField(replay, "lastUsedAt", lastUsedAt);
        return replay;
    }
}
