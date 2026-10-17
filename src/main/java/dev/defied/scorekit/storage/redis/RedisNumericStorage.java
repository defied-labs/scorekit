package dev.defied.scorekit.storage.redis;

import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.StoredEntry;
import redis.clients.jedis.UnifiedJedis;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

final class RedisNumericStorage implements LeaderboardStorage<Double> {

    private final UnifiedJedis jedis;
    private final String rankingKey;

    RedisNumericStorage(UnifiedJedis jedis, String rankingKey) {
        this.jedis = jedis;
        this.rankingKey = rankingKey;
    }

    @Override
    public void put(String key, Double score) {
        jedis.zadd(rankingKey, score, key);
    }

    @Override
    public Optional<Double> score(String key) {
        return Optional.ofNullable(jedis.zscore(rankingKey, key));
    }

    @Override
    public OptionalLong rank(String key) {
        Long rank = jedis.zrevrank(rankingKey, key);
        return rank == null ? OptionalLong.empty() : OptionalLong.of(rank + 1);
    }

    @Override
    public List<StoredEntry<Double>> top(int n) {
        if (n == 0) {
            return List.of();
        }
        return jedis.zrevrangeWithScores(rankingKey, 0, n - 1L).stream()
                .map(tuple -> new StoredEntry<>(tuple.getElement(), tuple.getScore()))
                .toList();
    }

    @Override
    public boolean remove(String key) {
        return jedis.zrem(rankingKey, key) > 0;
    }

    @Override
    public long size() {
        return jedis.zcard(rankingKey);
    }
}
