package dev.defied.scorekit.storage.memory;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.cache.CacheState;
import dev.defied.scorekit.storage.cache.CacheStorageFactory;

public final class InMemoryStorageFactory implements CacheStorageFactory {

    @Override
    public <S> LeaderboardStorage<S> create(LeaderboardId id, ScoreType<S> type) {
        return new InMemoryStorage<>(type);
    }

    @Override
    public CacheState cacheState(LeaderboardId id) {
        return new InMemoryCacheState();
    }
}
