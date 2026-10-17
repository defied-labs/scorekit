package dev.defied.scorekit;

import dev.defied.scorekit.codec.KeyCodec;
import dev.defied.scorekit.codec.ScoreCodec;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.StoredEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

final class StoredLeaderboard<K, V, S> implements Leaderboard<K, V> {

    private final String name;
    private final KeyCodec<K> keyCodec;
    private final ScoreCodec<V, S> scoreCodec;
    private final LeaderboardStorage<S> storage;

    StoredLeaderboard(String name, KeyCodec<K> keyCodec, ScoreCodec<V, S> scoreCodec, LeaderboardStorage<S> storage) {
        this.name = name;
        this.keyCodec = keyCodec;
        this.scoreCodec = scoreCodec;
        this.storage = storage;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void put(K key, V score) {
        Objects.requireNonNull(score, "score");
        storage.put(encode(key), scoreCodec.type().normalize(scoreCodec.encode(score)));
    }

    @Override
    public Optional<V> score(K key) {
        return storage.score(encode(key)).map(scoreCodec::decode);
    }

    @Override
    public OptionalLong rank(K key) {
        return storage.rank(encode(key));
    }

    @Override
    public List<LeaderboardEntry<K, V>> top(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must not be negative: " + n);
        }
        List<StoredEntry<S>> stored = storage.top(n);
        List<LeaderboardEntry<K, V>> entries = new ArrayList<>(stored.size());
        for (StoredEntry<S> entry : stored) {
            entries.add(new LeaderboardEntry<>(keyCodec.decode(entry.key()), scoreCodec.decode(entry.score()), entries.size() + 1L));
        }
        return entries;
    }

    @Override
    public boolean remove(K key) {
        return storage.remove(encode(key));
    }

    @Override
    public long size() {
        return storage.size();
    }

    private String encode(K key) {
        return keyCodec.encode(Objects.requireNonNull(key, "key"));
    }
}
