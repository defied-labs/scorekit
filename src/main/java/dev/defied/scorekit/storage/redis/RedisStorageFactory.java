package dev.defied.scorekit.storage.redis;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.StorageFactory;
import dev.defied.scorekit.storage.cache.CacheStorage;
import dev.defied.scorekit.storage.cache.CacheStorageFactory;
import redis.clients.jedis.UnifiedJedis;

import java.util.Objects;

public final class RedisStorageFactory implements StorageFactory, CacheStorageFactory {

    private final UnifiedJedis jedis;

    public RedisStorageFactory(UnifiedJedis jedis) {
        this.jedis = Objects.requireNonNull(jedis, "jedis");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> LeaderboardStorage<S> create(LeaderboardId id, ScoreType<S> type) {
        String prefix = prefix(id);
        if (type == ScoreType.NUMERIC) {
            return (LeaderboardStorage<S>) new RedisNumericStorage(jedis, prefix + "ranking");
        }
        return (LeaderboardStorage<S>) new RedisLexicographicStorage(jedis, prefix + "lex", prefix + "scores");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> CacheStorage<S> createCache(LeaderboardId id, ScoreType<S> type) {
        String prefix = prefix(id) + "cache:";
        RedisCacheState state = new RedisCacheState(jedis, prefix + "state", prefix + "lock", prefix + "sweep");
        if (type == ScoreType.NUMERIC) {
            return (CacheStorage<S>) new RedisNumericCache(jedis, prefix, state);
        }
        return (CacheStorage<S>) new RedisLexicographicCache(jedis, prefix, state);
    }

    private static String prefix(LeaderboardId id) {
        if (id.provider().indexOf(':') >= 0) {
            throw new IllegalArgumentException("Provider name must not contain ':' with Redis storage: " + id.provider());
        }
        return "scorekit:{" + id.provider() + ":" + id.leaderboard() + "}:";
    }
}
