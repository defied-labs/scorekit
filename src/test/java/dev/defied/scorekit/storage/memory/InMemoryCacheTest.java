package dev.defied.scorekit.storage.memory;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.cache.CacheStorage;
import dev.defied.scorekit.storage.cache.CacheStorageContract;

class InMemoryCacheTest extends CacheStorageContract {

    @Override
    protected <S> CacheStorage<S> createCache(ScoreType<S> type) {
        return new InMemoryStorageFactory().createCache(new LeaderboardId("test", "board"), type);
    }
}
