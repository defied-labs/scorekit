package dev.defied.scorekit.storage.cache;

import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.StorageFactory;

public interface CacheStorageFactory extends StorageFactory {

    CacheState cacheState(LeaderboardId id);
}
