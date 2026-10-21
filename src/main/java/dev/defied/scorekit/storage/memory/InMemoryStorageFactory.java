package dev.defied.scorekit.storage.memory;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.VersionedStorage;
import dev.defied.scorekit.storage.VersionedStorageFactory;
import dev.defied.scorekit.storage.cache.CacheStorage;
import dev.defied.scorekit.storage.cache.CacheStorageFactory;

public final class InMemoryStorageFactory implements VersionedStorageFactory, CacheStorageFactory {

    @Override
    public <S> VersionedStorage<S> create(LeaderboardId id, ScoreType<S> type) {
        return new InMemoryStorage<>(type);
    }

    @Override
    public <S> CacheStorage<S> createCache(LeaderboardId id, ScoreType<S> type) {
        return new InMemoryCache<>(type);
    }
}
