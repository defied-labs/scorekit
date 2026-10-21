package dev.defied.scorekit.storage.cache;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.StorageFactory;

import java.util.Objects;

public final class CachedStorageFactory implements StorageFactory {

    private final CacheStorageFactory cache;
    private final StorageFactory primary;

    public CachedStorageFactory(CacheStorageFactory cache, StorageFactory primary) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.primary = Objects.requireNonNull(primary, "primary");
    }

    @Override
    public <S> LeaderboardStorage<S> create(LeaderboardId id, ScoreType<S> type) {
        return new CachedStorage<>(primary.create(id, type), cache.create(id, type), cache.cacheState(id));
    }
}
