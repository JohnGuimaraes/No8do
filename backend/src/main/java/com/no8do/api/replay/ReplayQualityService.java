package com.no8do.api.replay;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReplayQualityService {
    private final ReplayRelationRepository relationRepository;

    public ReplayQualityService(ReplayRelationRepository relationRepository) { this.relationRepository = relationRepository; }

    public ReplayQualityResponse assess(Replay replay) {
        List<String> signals = new ArrayList<>();
        int score = switch (replay.getStatus()) {
            case VALIDATED -> { signals.add("Conhecimento validado"); yield 45; }
            case DRAFT -> { signals.add("Conhecimento ainda em rascunho"); yield 20; }
            case DEPRECATED -> { signals.add("Conhecimento descontinuado"); yield 5; }
        };
        int known = replay.getSuccessCount() + replay.getFailureCount();
        Integer successRate = known == 0 ? null : Math.round((replay.getSuccessCount() * 100f) / known);
        if (known > 0) {
            int evidence = Math.round(Math.min(25, known * 3) * (replay.getSuccessCount() / (float) known));
            score += evidence;
            signals.add(replay.getSuccessCount() + " reutilização(ões) com sucesso");
            if (replay.getFailureCount() > 0) signals.add(replay.getFailureCount() + " falha(s) registrada(s)");
        } else if (replay.getUsageCount() > 0) {
            signals.add("Reutilizações sem resultado conclusivo");
        }
        if (replay.getVersion() > 1) { score += 5; signals.add("Possui histórico de versões"); }
        if (replay.getLastUsedAt() != null && replay.getLastUsedAt().isAfter(Instant.now().minus(Duration.ofDays(90)))) { score += 5; signals.add("Uso recente"); }
        boolean superseded = relationRepository.existsByWorkspaceIdAndTargetReplayIdAndType(replay.getWorkspace().getId(), replay.getId(), ReplayRelationType.SUPERSEDES);
        if (superseded) { score -= 25; signals.add("Substituído por conhecimento mais atual"); }
        if (replay.getStatus() == ReplayStatus.DRAFT) score = Math.min(score, 49);
        if (replay.getStatus() == ReplayStatus.DEPRECATED) score = Math.min(score, 20);
        score = Math.max(0, Math.min(100, score));
        ReplayQualityLevel level = replay.getStatus() == ReplayStatus.DEPRECATED || score < 35 ? ReplayQualityLevel.LOW : score < 65 ? ReplayQualityLevel.MEDIUM : ReplayQualityLevel.HIGH;
        return new ReplayQualityResponse(score, level, replay.getUsageCount(), replay.getSuccessCount(), replay.getFailureCount(), successRate, signals);
    }
}
