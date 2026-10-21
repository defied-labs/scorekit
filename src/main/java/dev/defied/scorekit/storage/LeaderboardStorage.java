package dev.defied.scorekit.storage;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

public interface LeaderboardStorage<S> {

    void put(String key, S score);

    Optional<S> score(String key);

    OptionalLong rank(String key);

    List<StoredEntry<S>> top(int n);

    boolean remove(String key);

    long size();

    ScanPage<S> scan(String cursor, int count);

    void clear();
}
