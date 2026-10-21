package dev.defied.scorekit.storage.cache;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.StorageFactory;
import dev.defied.scorekit.storage.VersionedStorageFactory;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class CachedStorageFactory implements StorageFactory {

    private static final ScheduledExecutorService SWEEPER = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("scorekit-cache-sweeper").factory());

    private final CacheStorageFactory cache;
    private final VersionedStorageFactory primary;

    public CachedStorageFactory(CacheStorageFactory cache, VersionedStorageFactory primary) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.primary = Objects.requireNonNull(primary, "primary");
    }

    @Override
    public <S> LeaderboardStorage<S> create(LeaderboardId id, ScoreType<S> type) {
        CachedStorage<S> storage = new CachedStorage<>(primary.create(id, type), cache.createCache(id, type));
        SWEEPER.scheduleWithFixedDelay(storage::sweepInBackground,
                CachedStorage.SWEEP_INTERVAL_MILLIS, CachedStorage.SWEEP_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        return storage;
    }
}
