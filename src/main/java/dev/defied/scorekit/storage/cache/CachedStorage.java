package dev.defied.scorekit.storage.cache;

import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.ScanPage;
import dev.defied.scorekit.storage.StoredEntry;
import dev.defied.scorekit.storage.VersionedStorage;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static java.lang.System.Logger.Level.WARNING;

final class CachedStorage<S> implements LeaderboardStorage<S> {

    static final long SWEEP_INTERVAL_MILLIS = 10_000;

    private static final int BATCH = 500;
    private static final long SWEEP_MARGIN_MICROS = 20_000_000;
    private static final long TOMBSTONE_RETENTION_MICROS = 3_600_000_000L;
    private static final long UNAVAILABLE_BACKOFF_MILLIS = 5_000;
    private static final System.Logger LOGGER = System.getLogger(CachedStorage.class.getName());

    private final VersionedStorage<S> primary;
    private final CacheStorage<S> cache;
    private final CacheState state;
    private final AtomicBoolean warming = new AtomicBoolean();
    private final AtomicBoolean sweeping = new AtomicBoolean();
    private volatile long unavailableUntil;

    CachedStorage(VersionedStorage<S> primary, CacheStorage<S> cache) {
        this.primary = primary;
        this.cache = cache;
        this.state = cache.state();
    }

    @Override
    public void put(String key, S score) {
        long version = primary.putVersioned(key, score);
        writeCache(() -> cache.putIfNewer(key, score, version));
    }

    @Override
    public Optional<S> score(String key) {
        return read(() -> cache.score(key), () -> primary.score(key));
    }

    @Override
    public OptionalLong rank(String key) {
        return read(() -> cache.rank(key), () -> primary.rank(key));
    }

    @Override
    public List<StoredEntry<S>> top(int n) {
        return read(() -> cache.top(n), () -> primary.top(n));
    }

    @Override
    public boolean remove(String key) {
        OptionalLong version = primary.removeVersioned(key);
        version.ifPresent(removed -> writeCache(() -> cache.removeIfNewer(key, removed)));
        return version.isPresent();
    }

    @Override
    public long size() {
        return read(cache::size, primary::size);
    }

    void warmUp() {
        OptionalLong begun = state.beginWarmUp();
        if (begun.isEmpty()) {
            return;
        }
        long epoch = begun.getAsLong();
        long watermark = VersionedStorage.now() - SWEEP_MARGIN_MICROS;
        boolean completed = false;
        try {
            cache.clear();
            String cursor = null;
            do {
                ScanPage<S> page = primary.scan(cursor, BATCH);
                cache.applyAll(page.entries());
                if (!state.renewWarmUp(epoch)) {
                    return;
                }
                cursor = page.next();
            } while (cursor != null);
            completed = state.completeWarmUp(epoch, watermark);
        } finally {
            if (!completed) {
                state.abortWarmUp(epoch);
            }
        }
    }

    void sweep() {
        OptionalLong watermark = state.tryStartSweep(SWEEP_INTERVAL_MILLIS);
        if (watermark.isEmpty()) {
            return;
        }
        long next = VersionedStorage.now() - SWEEP_MARGIN_MICROS;
        String cursor = null;
        do {
            ScanPage<S> page = primary.scanChanges(watermark.getAsLong(), cursor, BATCH);
            cache.applyAll(page.entries());
            cursor = page.next();
        } while (cursor != null);
        primary.purgeRemoved(VersionedStorage.now() - TOMBSTONE_RETENTION_MICROS);
        state.completeSweep(next);
    }

    void sweepInBackground() {
        inBackground(sweeping, "scorekit-cache-sweep", this::sweep);
    }

    private void writeCache(Runnable write) {
        if (unavailable()) {
            return;
        }
        try {
            write.run();
        } catch (RuntimeException e) {
            markUnavailable("Cache write failed, the next sweep will repair it", e);
        }
    }

    private <T> T read(Supplier<T> fromCache, Supplier<T> fromPrimary) {
        if (!unavailable()) {
            try {
                return fromCache.get();
            } catch (CacheColdException e) {
                inBackground(warming, "scorekit-cache-warm-up", this::warmUp);
            } catch (RuntimeException e) {
                markUnavailable("Cache read failed, reading from primary storage", e);
            }
        }
        return fromPrimary.get();
    }

    private boolean unavailable() {
        return System.currentTimeMillis() < unavailableUntil;
    }

    private void markUnavailable(String message, RuntimeException e) {
        unavailableUntil = System.currentTimeMillis() + UNAVAILABLE_BACKOFF_MILLIS;
        LOGGER.log(WARNING, message, e);
    }

    private static void inBackground(AtomicBoolean running, String name, Runnable task) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread.ofVirtual().name(name).start(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                LOGGER.log(WARNING, name + " failed", e);
            } finally {
                running.set(false);
            }
        });
    }
}
