package dev.defied.scorekit.storage;

import dev.defied.scorekit.codec.ScoreType;

public interface VersionedStorageFactory extends StorageFactory {

    @Override
    <S> VersionedStorage<S> create(LeaderboardId id, ScoreType<S> type);

    @Override
    default <S> VersionedStorage<S> create(LeaderboardId id, ScoreType<S> type, StorageSettings settings) {
        VersionedStorage<S> storage = create(id, type);
        return settings.rankSampling() == null ? storage : new SampledRankStorage<>(storage, type, settings.rankSampling());
    }
}
