package com.no8do.api.replay;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ReplayVersionRepository extends JpaRepository<ReplayVersion, UUID> { List<ReplayVersion> findByReplayIdOrderByVersionDesc(UUID replayId); Optional<ReplayVersion> findByReplayIdAndVersion(UUID replayId,int version); }
