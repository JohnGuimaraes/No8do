package com.no8do.api.replay.embedding;

import com.no8do.api.replay.ReplayStatus;
import com.no8do.api.replay.ReplayVersionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplayEmbeddingBackfillService {
    private static final List<ReplayStatus> ELIGIBLE_STATUSES = List.of(
            ReplayStatus.DRAFT, ReplayStatus.VALIDATED, ReplayStatus.DEPRECATED);

    private final ReplayVersionRepository replayVersionRepository;
    private final ReplayEmbeddingService replayEmbeddingService;

    public ReplayEmbeddingBackfillService(
            ReplayVersionRepository replayVersionRepository,
            ReplayEmbeddingService replayEmbeddingService) {
        this.replayVersionRepository = replayVersionRepository;
        this.replayEmbeddingService = replayEmbeddingService;
    }

    @Transactional
    public ReplayEmbeddingBackfillResult backfill(UUID workspaceId, EmbeddingProvider provider, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize deve ser maior que zero.");
        }

        long examined = 0;
        long processed = 0;
        long generated = 0;
        long reused = 0;
        int pageNumber = 0;
        Page<com.no8do.api.replay.ReplayVersion> page;

        do {
            page = replayVersionRepository.findEligibleCurrentByWorkspaceId(
                    workspaceId, ELIGIBLE_STATUSES, PageRequest.of(pageNumber, batchSize));
            for (var replayVersion : page.getContent()) {
                examined++;
                ReplayEmbeddingOutcome outcome = replayEmbeddingService.ensureEmbedding(replayVersion, provider);
                processed++;
                if (outcome.reused()) {
                    reused++;
                } else {
                    generated++;
                }
            }
            pageNumber++;
        } while (page.hasNext());

        return new ReplayEmbeddingBackfillResult(examined, processed, generated, reused);
    }
}
