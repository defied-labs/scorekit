package dev.defied.scorekit.storage.cache;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;

public interface CacheStorageFactory {

    <S> CacheStorage<S> createCache(LeaderboardId id, ScoreType<S> type);
}
