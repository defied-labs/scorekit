package dev.defied.scorekit.storage.memory;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.VersionedStorage;
import dev.defied.scorekit.storage.VersionedStorageContract;

class InMemoryStorageTest extends VersionedStorageContract {

    @Override
    protected <S> VersionedStorage<S> create(ScoreType<S> type) {
        return new InMemoryStorage<>(type);
    }
}
