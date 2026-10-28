package dev.defied.scorekit.storage;

import dev.defied.scorekit.codec.ScoreType;

public interface StorageFactory {

    <S> LeaderboardStorage<S> create(LeaderboardId id, ScoreType<S> type);

    default <S> LeaderboardStorage<S> create(LeaderboardId id, ScoreType<S> type, StorageSettings settings) {
        return create(id, type);
    }
}
