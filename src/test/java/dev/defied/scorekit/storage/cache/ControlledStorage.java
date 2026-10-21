package dev.defied.scorekit.storage.cache;

import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.ScanPage;
import dev.defied.scorekit.storage.StoredEntry;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

final class ControlledStorage<S> implements LeaderboardStorage<S> {

    private final LeaderboardStorage<S> delegate;
    volatile boolean failing;
    volatile Runnable afterScan = () -> {
    };

    ControlledStorage(LeaderboardStorage<S> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void put(String key, S score) {
        check();
        delegate.put(key, score);
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
    public boolean remove(String key) {
        check();
        return delegate.remove(key);
    }

    @Override
    public long size() {
        check();
        return delegate.size();
    }

    @Override
    public ScanPage<S> scan(String cursor, int count) {
        check();
        ScanPage<S> page = delegate.scan(cursor, count);
        afterScan.run();
        return page;
    }

    @Override
    public void clear() {
        check();
        delegate.clear();
    }

    private void check() {
        if (failing) {
            throw new IllegalStateException("Storage unavailable");
        }
    }
}
