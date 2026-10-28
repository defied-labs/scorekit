package dev.defied.scorekit.storage.cache;

import dev.defied.scorekit.storage.StoredEntry;
import dev.defied.scorekit.storage.VersionedEntry;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

final class ControlledCache<S> implements CacheStorage<S> {

    private final CacheStorage<S> delegate;
    final AtomicInteger calls = new AtomicInteger();
    volatile boolean failing;

    ControlledCache(CacheStorage<S> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<S> score(String key) {
        check();
        return delegate.score(key);
    }

    @Override
    public OptionalLong rank(String key) {
        check();
        return delegate.rank(key);
    }

    @Override
    public List<StoredEntry<S>> top(int n) {
        check();
        return delegate.top(n);
    }

    @Override
    public long size() {
        check();
        return delegate.size();
    }

    @Override
    public void putIfNewer(String key, S score, long version) {
        check();
        delegate.putIfNewer(key, score, version);
    }

    @Override
    public void removeIfNewer(String key, long version) {
        check();
        delegate.removeIfNewer(key, version);
    }

    @Override
    public void applyAll(List<VersionedEntry<S>> entries) {
        check();
        delegate.applyAll(entries);
    }

    @Override
    public void clear() {
        check();
        delegate.clear();
    }

    @Override
    public boolean evictIfIdle(long idleBeforeMillis) {
        check();
        return delegate.evictIfIdle(idleBeforeMillis);
    }

    @Override
    public CacheState state() {
        return delegate.state();
    }

    private void check() {
        calls.incrementAndGet();
        if (failing) {
            throw new IllegalStateException("Cache unavailable");
        }
    }
}
