package com.no8do.api.replay;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplayUsageRepository extends JpaRepository<ReplayUsage, UUID> {

    List<ReplayUsage> findByReplayIdOrderByUsedAtDesc(UUID replayId);
}
