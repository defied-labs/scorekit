package dev.defied.scorekit.storage;

import dev.defied.scorekit.codec.ScoreType;

public interface VersionedStorageFactory extends StorageFactory {

    @Override
    <S> VersionedStorage<S> create(LeaderboardId id, ScoreType<S> type);
}
