package dev.defied.scorekit.storage.cache;

import dev.defied.scorekit.storage.ScanPage;
import dev.defied.scorekit.storage.StoredEntry;
import dev.defied.scorekit.storage.VersionedStorage;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

final class ControlledPrimary<S> implements VersionedStorage<S> {

    private final VersionedStorage<S> delegate;
    volatile Runnable afterScan = () -> {
    };

    ControlledPrimary(VersionedStorage<S> delegate) {
        this.delegate = delegate;
    }

    @Override
    public long putVersioned(String key, S score) {
        return delegate.putVersioned(key, score);
    }

    @Override
    public OptionalLong removeVersioned(String key) {
        return delegate.removeVersioned(key);
    }

    @Override
    public ScanPage<S> scan(String cursor, int count) {
        ScanPage<S> page = delegate.scan(cursor, count);
        afterScan.run();
        return page;
    }

    @Override
    public ScanPage<S> scanChanges(long sinceVersion, String cursor, int count) {
        return delegate.scanChanges(sinceVersion, cursor, count);
    }

    @Override
    public void purgeRemoved(long beforeVersion) {
        delegate.purgeRemoved(beforeVersion);
    }

    @Override
    public Optional<StoredEntry<S>> entryBehind(StoredEntry<S> position, int offset) {
        return delegate.entryBehind(position, offset);
    }

    @Override
    public long countBetween(StoredEntry<S> ahead, StoredEntry<S> behind) {
        return delegate.countBetween(ahead, behind);
    }

    @Override
    public Optional<S> score(String key) {
        return delegate.score(key);
    }

    @Override
    public OptionalLong rank(String key) {
        return delegate.rank(key);
    }

    @Override
    public List<StoredEntry<S>> top(int n) {
        return delegate.top(n);
    }

    @Override
    public long size() {
        return delegate.size();
    }
}
