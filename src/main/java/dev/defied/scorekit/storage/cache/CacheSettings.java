package dev.defied.scorekit.storage.cache;

import java.time.Duration;

record CacheSettings(Duration sweepInterval, Duration retryDelay, Duration idleTimeout) {

    static final CacheSettings DEFAULT = new CacheSettings(Duration.ofSeconds(10), Duration.ofSeconds(5), Duration.ZERO);
}
