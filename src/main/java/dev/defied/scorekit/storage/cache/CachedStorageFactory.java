package dev.defied.scorekit.storage.cache;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.StorageFactory;
import dev.defied.scorekit.storage.VersionedStorageFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class CachedStorageFactory implements StorageFactory, AutoCloseable {

    private static final Duration MAX_SWEEP_INTERVAL = Duration.ofMinutes(10);
    private static final ScheduledExecutorService SWEEPER = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("scorekit-cache-sweeper").factory());

    private final CacheStorageFactory cache;
    private final VersionedStorageFactory primary;
    private final CacheSettings settings;
    private final List<ScheduledFuture<?>> sweeps = new ArrayList<>();
    private boolean closed;

    public CachedStorageFactory(CacheStorageFactory cache, VersionedStorageFactory primary) {
        this(cache, primary, CacheSettings.DEFAULT);
    }

    private CachedStorageFactory(CacheStorageFactory cache, VersionedStorageFactory primary, CacheSettings settings) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.primary = Objects.requireNonNull(primary, "primary");
        this.settings = settings;
    }

    public static Builder builder(CacheStorageFactory cache, VersionedStorageFactory primary) {
        return new Builder(cache, primary);
    }

    @Override
    public synchronized <S> LeaderboardStorage<S> create(LeaderboardId id, ScoreType<S> type) {
        if (closed) {
            throw new IllegalStateException("Storage factory is closed");
        }
        CachedStorage<S> storage = new CachedStorage<>(primary.create(id, type), cache.createCache(id, type), settings);
        long interval = settings.sweepInterval().toMillis();
        sweeps.add(SWEEPER.scheduleWithFixedDelay(storage::sweepInBackground, interval, interval, TimeUnit.MILLISECONDS));
        return storage;
    }

    @Override
    public synchronized void close() {
        closed = true;
        sweeps.forEach(sweep -> sweep.cancel(false));
        sweeps.clear();
    }

    public static final class Builder {

        private final CacheStorageFactory cache;
        private final VersionedStorageFactory primary;
        private Duration sweepInterval = CacheSettings.DEFAULT.sweepInterval();
        private Duration retryDelay = CacheSettings.DEFAULT.retryDelay();

        private Builder(CacheStorageFactory cache, VersionedStorageFactory primary) {
            this.cache = cache;
            this.primary = primary;
        }

        public Builder sweepInterval(Duration interval) {
            if (interval.toMillis() < 1 || interval.compareTo(MAX_SWEEP_INTERVAL) > 0) {
                throw new IllegalArgumentException("Sweep interval must be between 1 ms and " + MAX_SWEEP_INTERVAL + ": " + interval);
            }
            sweepInterval = interval;
            return this;
        }

        public Builder retryDelay(Duration delay) {
            if (delay.isNegative()) {
                throw new IllegalArgumentException("Retry delay must not be negative: " + delay);
            }
            retryDelay = delay;
            return this;
        }

        public CachedStorageFactory build() {
            return new CachedStorageFactory(cache, primary, new CacheSettings(sweepInterval, retryDelay));
        }
    }
}
