package dev.defied.scorekit.storage;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.OptionalLong;

public interface VersionedStorage<S> extends LeaderboardStorage<S> {

    long putVersioned(String key, S score);

    OptionalLong removeVersioned(String key);

    ScanPage<S> scan(String cursor, int count);

    ScanPage<S> scanChanges(long sinceVersion, String cursor, int count);

    void purgeRemoved(long beforeVersion);

    Optional<StoredEntry<S>> entryBehind(StoredEntry<S> position, int offset);

    long countBetween(StoredEntry<S> ahead, StoredEntry<S> behind);

    @Override
    default void put(String key, S score) {
        putVersioned(key, score);
    }

    @Override
    default boolean remove(String key) {
        return removeVersioned(key).isPresent();
    }

    static long now() {
        return ChronoUnit.MICROS.between(Instant.EPOCH, Instant.now());
    }

    static long next(long current) {
        return Math.max(current + 1, now());
    }
}
