package dev.defied.scorekit.storage.memory;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.StorageFactory;

public final class InMemoryStorageFactory implements StorageFactory {

    @Override
    public <S> LeaderboardStorage<S> create(LeaderboardId id, ScoreType<S> type) {
        return new InMemoryStorage<>(type);
    }
}
