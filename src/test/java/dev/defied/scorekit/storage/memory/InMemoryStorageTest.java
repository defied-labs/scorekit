package dev.defied.scorekit.storage.memory;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.LeaderboardStorageContract;

class InMemoryStorageTest extends LeaderboardStorageContract {

    @Override
    protected <S> LeaderboardStorage<S> create(ScoreType<S> type) {
        return new InMemoryStorage<>(type);
    }
}
