package com.no8do.api.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Bounded per-process limiter. Deployments with multiple instances must add a shared edge limiter. */
@Component
public class IntegrationBootstrapRateLimiter {
    private static final int MAX_KEYS = 10_000;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new LinkedHashMap<>();

    public IntegrationBootstrapRateLimiter(Clock clock) { this.clock = clock; }

    public synchronized int consume(String namespace, String key, int limit, Duration window) {
        Instant now = clock.instant();
        String bucketKey = namespace + ":" + key;
        prune(now);
        Bucket bucket = buckets.get(bucketKey);
        if (bucket == null) {
            if (buckets.size() >= MAX_KEYS) return (int) Math.max(1, window.toSeconds());
            bucket = new Bucket(now.plus(window));
            buckets.put(bucketKey, bucket);
        }
        if (!now.isBefore(bucket.resetAt)) {
            bucket.count = 0;
            bucket.resetAt = now.plus(window);
        }
        if (bucket.count >= limit) return (int) Math.max(1, Duration.between(now, bucket.resetAt).toSeconds());
        bucket.count++;
        return 0;
    }

    private void prune(Instant now) {
        Iterator<Bucket> iterator = buckets.values().iterator();
        while (iterator.hasNext()) if (!now.isBefore(iterator.next().resetAt)) iterator.remove();
    }

    private static final class Bucket {
        private Instant resetAt;
        private int count;
        private Bucket(Instant resetAt) { this.resetAt = resetAt; }
    }
}
