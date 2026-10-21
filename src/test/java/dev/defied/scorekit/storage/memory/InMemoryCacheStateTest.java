package dev.defied.scorekit.storage.memory;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.cache.CacheState;
import dev.defied.scorekit.storage.cache.CacheStateContract;

class InMemoryCacheStateTest extends CacheStateContract {

    @Override
    protected CacheState create() {
        return new InMemoryStorageFactory().createCache(new LeaderboardId("test", "board"), ScoreType.NUMERIC).state();
    }
}
