package dev.defied.scorekit;

public record LeaderboardEntry<K, V>(K key, V score, long rank) {
}
