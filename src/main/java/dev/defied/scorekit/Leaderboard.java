package dev.defied.scorekit;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

public interface Leaderboard<K, V> {

    String name();

    void put(K key, V score);

    Optional<V> score(K key);

    OptionalLong rank(K key);

    List<LeaderboardEntry<K, V>> top(int n);

    boolean remove(K key);

    long size();
}
