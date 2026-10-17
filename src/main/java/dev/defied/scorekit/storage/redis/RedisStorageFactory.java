package dev.defied.scorekit.storage.redis;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.StorageFactory;
import redis.clients.jedis.UnifiedJedis;

import java.util.Objects;

public final class RedisStorageFactory implements StorageFactory {

    private final UnifiedJedis jedis;

    public RedisStorageFactory(UnifiedJedis jedis) {
        this.jedis = Objects.requireNonNull(jedis, "jedis");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> LeaderboardStorage<S> create(LeaderboardId id, ScoreType<S> type) {
        if (id.provider().indexOf(':') >= 0) {
            throw new IllegalArgumentException("Provider name must not contain ':' with Redis storage: " + id.provider());
        }
        String prefix = "scorekit:{" + id.provider() + ":" + id.leaderboard() + "}:";
        if (type == ScoreType.NUMERIC) {
            return (LeaderboardStorage<S>) new RedisNumericStorage(jedis, prefix + "ranking");
        }
        return (LeaderboardStorage<S>) new RedisLexicographicStorage(jedis, prefix + "lex", prefix + "scores");
    }
}
