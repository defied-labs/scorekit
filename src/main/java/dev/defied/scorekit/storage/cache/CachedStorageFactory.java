package dev.defied.scorekit.storage.cache;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.StorageFactory;
import dev.defied.scorekit.storage.VersionedStorageFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class CachedStorageFactory implements StorageFactory, AutoCloseable {

    private static final ScheduledExecutorService SWEEPER = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("scorekit-cache-sweeper").factory());

    private final CacheStorageFactory cache;
    private final VersionedStorageFactory primary;
    private final List<ScheduledFuture<?>> sweeps = new ArrayList<>();
    private boolean closed;

    public CachedStorageFactory(CacheStorageFactory cache, VersionedStorageFactory primary) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.primary = Objects.requireNonNull(primary, "primary");
    }

    @Override
    public synchronized <S> LeaderboardStorage<S> create(LeaderboardId id, ScoreType<S> type) {
        if (closed) {
            throw new IllegalStateException("Storage factory is closed");
        }
        CachedStorage<S> storage = new CachedStorage<>(primary.create(id, type), cache.createCache(id, type));
        sweeps.add(SWEEPER.scheduleWithFixedDelay(storage::sweepInBackground,
                CachedStorage.SWEEP_INTERVAL_MILLIS, CachedStorage.SWEEP_INTERVAL_MILLIS, TimeUnit.MILLISECONDS));
        return storage;
    }

    @Override
    public synchronized void close() {
        closed = true;
        sweeps.forEach(sweep -> sweep.cancel(false));
        sweeps.clear();
    }
}
