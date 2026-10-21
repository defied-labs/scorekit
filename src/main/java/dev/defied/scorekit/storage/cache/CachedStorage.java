package dev.defied.scorekit.storage.cache;

import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.ScanPage;
import dev.defied.scorekit.storage.StoredEntry;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static java.lang.System.Logger.Level.WARNING;

final class CachedStorage<S> implements LeaderboardStorage<S> {

    private static final int BATCH = 500;
    private static final int LOCK_STRIPES = 64;
    private static final System.Logger LOGGER = System.getLogger(CachedStorage.class.getName());

    private final LeaderboardStorage<S> primary;
    private final LeaderboardStorage<S> cache;
    private final CacheState state;
    private final Lock[] locks = new Lock[LOCK_STRIPES];
    private final AtomicBoolean warming = new AtomicBoolean();
    private final AtomicBoolean invalidationPending = new AtomicBoolean();

    CachedStorage(LeaderboardStorage<S> primary, LeaderboardStorage<S> cache, CacheState state) {
        this.primary = primary;
        this.cache = cache;
        this.state = state;
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    @Override
    public void put(String key, S score) {
        Lock lock = lock(key);
        lock.lock();
        try {
            primary.put(key, score);
            afterWrite(key, () -> cache.put(key, score));
        } finally {
            lock.unlock();
        }
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
        Lock lock = lock(key);
        lock.lock();
        try {
            boolean removed = primary.remove(key);
            afterWrite(key, () -> cache.remove(key));
            return removed;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long size() {
        return read(cache::size, primary::size);
    }

    @Override
    public ScanPage<S> scan(String cursor, int count) {
        return primary.scan(cursor, count);
    }

    @Override
    public void clear() {
        primary.clear();
        invalidate();
    }

    void warmUp() {
        OptionalLong begun = state.beginWarmUp();
        if (begun.isEmpty()) {
            return;
        }
        long epoch = begun.getAsLong();
        boolean completed = false;
        try {
            cache.clear();
            String cursor = null;
            do {
                ScanPage<S> page = primary.scan(cursor, BATCH);
                page.entries().forEach(entry -> cache.put(entry.key(), entry.score()));
                if (!state.renewWarmUp(epoch)) {
                    return;
                }
                cursor = page.next();
            } while (cursor != null);
            while (state.renewWarmUp(epoch)) {
                List<String> dirty = state.pollDirty(BATCH);
                dirty.forEach(this::refresh);
                if (dirty.isEmpty() && state.completeWarmUp(epoch)) {
                    completed = true;
                    return;
                }
            }
        } finally {
            if (!completed) {
                state.abortWarmUp(epoch);
            }
        }
    }

    private void refresh(String key) {
        primary.score(key).ifPresentOrElse(score -> cache.put(key, score), () -> cache.remove(key));
    }

    private void afterWrite(String key, Runnable cacheWrite) {
        try {
            flushInvalidation();
            if (!state.markDirtyUnlessWarm(key)) {
                cacheWrite.run();
            }
        } catch (RuntimeException e) {
            LOGGER.log(WARNING, "Cache write failed, invalidating cached leaderboard", e);
            invalidate();
        }
    }

    private <T> T read(Supplier<T> fromCache, Supplier<T> fromPrimary) {
        try {
            flushInvalidation();
            if (state.isWarm()) {
                return fromCache.get();
            }
            warmUpInBackground();
        } catch (RuntimeException e) {
            LOGGER.log(WARNING, "Cache read failed, reading from primary storage", e);
        }
        return fromPrimary.get();
    }

    private void warmUpInBackground() {
        if (!warming.compareAndSet(false, true)) {
            return;
        }
        Thread.ofVirtual().name("scorekit-cache-warm-up").start(() -> {
            try {
                warmUp();
            } catch (RuntimeException e) {
                LOGGER.log(WARNING, "Cache warm-up failed", e);
            } finally {
                warming.set(false);
            }
        });
    }

    private void invalidate() {
        invalidationPending.set(true);
        try {
            flushInvalidation();
        } catch (RuntimeException e) {
            LOGGER.log(WARNING, "Cache invalidation failed, retrying on next operation", e);
        }
    }

    private void flushInvalidation() {
        if (invalidationPending.compareAndSet(true, false)) {
            try {
                state.invalidate();
            } catch (RuntimeException e) {
                invalidationPending.set(true);
                throw e;
            }
        }
    }

    private Lock lock(String key) {
        return locks[Math.floorMod(key.hashCode(), LOCK_STRIPES)];
    }
}
