package dev.defied.scorekit.storage.memory;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.StoredEntry;
import dev.defied.scorekit.storage.VersionedEntry;
import dev.defied.scorekit.storage.cache.CacheColdException;
import dev.defied.scorekit.storage.cache.CacheState;
import dev.defied.scorekit.storage.cache.CacheStorage;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

final class InMemoryCache<S> implements CacheStorage<S> {

    private static final long TOMBSTONE_TTL_MILLIS = 300_000;

    private final InMemoryStorage<S> storage;
    private final InMemoryCacheState state = new InMemoryCacheState();

    InMemoryCache(ScoreType<S> type) {
        storage = new InMemoryStorage<>(type);
    }

    @Override
    public Optional<S> score(String key) {
        requireWarm();
        return storage.score(key);
    }

    @Override
    public OptionalLong rank(String key) {
        requireWarm();
        return storage.rank(key);
    }

    @Override
    public List<StoredEntry<S>> top(int n) {
        requireWarm();
        return storage.top(n);
    }

    @Override
    public long size() {
        requireWarm();
        return storage.size();
    }

    @Override
    public void putIfNewer(String key, S score, long version) {
        if (state.isAccepting()) {
            storage.putIfNewer(key, score, version);
        }
    }

    @Override
    public void removeIfNewer(String key, long version) {
        if (state.isAccepting()) {
            storage.removeIfNewer(key, version);
            storage.expireRemoved(System.currentTimeMillis() - TOMBSTONE_TTL_MILLIS);
        }
    }

    @Override
    public void applyAll(List<VersionedEntry<S>> entries) {
        for (VersionedEntry<S> entry : entries) {
            if (entry.isRemoved()) {
                removeIfNewer(entry.key(), entry.version());
            } else {
                putIfNewer(entry.key(), entry.score(), entry.version());
            }
        }
    }

    @Override
    public void clear() {
        storage.clear();
    }

    @Override
    public boolean evictIfIdle(long idleBeforeMillis) {
        return state.evictIfIdle(idleBeforeMillis, storage::clear);
    }

    @Override
    public CacheState state() {
        return state;
    }

    private void requireWarm() {
        if (!state.isWarm()) {
            throw new CacheColdException();
        }
    }
}
