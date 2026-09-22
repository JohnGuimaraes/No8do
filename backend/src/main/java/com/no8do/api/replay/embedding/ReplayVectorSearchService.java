package com.no8do.api.replay.embedding;

import com.no8do.api.replay.ReplayVersion;
import com.no8do.api.replay.ReplayVersionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplayVectorSearchService {
    private static final int MAX_TOP_K = 100;
    private final ReplayEmbeddingRepository embeddingRepository;
    private final ReplayVersionRepository versionRepository;

    public ReplayVectorSearchService(ReplayEmbeddingRepository embeddingRepository, ReplayVersionRepository versionRepository) {
        this.embeddingRepository = embeddingRepository;
        this.versionRepository = versionRepository;
    }

    @Transactional(readOnly = true)
    public List<ReplayVectorSearchHit> search(UUID workspaceId, String query, EmbeddingProvider provider, int topK) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query não pode ser vazia.");
        if (topK < 1 || topK > MAX_TOP_K) throw new IllegalArgumentException("topK deve estar entre 1 e " + MAX_TOP_K + ".");
        EmbeddingProviderDescriptor descriptor = provider.descriptor();
        EmbeddingResult result = provider.embed(query);
        if (result.dimensions() != descriptor.dimensions()) throw new IllegalStateException("Dimensões retornadas pelo provider não correspondem ao descriptor.");
        var rows = embeddingRepository.searchCurrentByVector(workspaceId, descriptor.provider(), descriptor.model(), descriptor.dimensions(), vector(result.vector()), topK);
        Map<UUID, ReplayVersion> versions = versionRepository.findAllById(rows.stream().map(ReplayVectorSearchRow::getReplayVersionId).toList()).stream().collect(java.util.stream.Collectors.toMap(ReplayVersion::getId, Function.identity()));
        return rows.stream().map(row -> {
            ReplayVersion version = versions.get(row.getReplayVersionId());
            if (version == null) throw new IllegalStateException("ReplayVersion retornada pela busca vetorial não foi encontrada.");
            return new ReplayVectorSearchHit(version, row.getDistance());
        }).toList();
    }

    private String vector(float[] values) {
        StringBuilder value = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) { if (index > 0) value.append(','); value.append(Float.toString(values[index])); }
        return value.append(']').toString();
    }
}
