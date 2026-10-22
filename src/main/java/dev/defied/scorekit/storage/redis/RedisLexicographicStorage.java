package dev.defied.scorekit.storage.redis;

import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.StoredEntry;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.ZRangeParams;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

final class RedisLexicographicStorage implements LeaderboardStorage<String> {

    private static final RedisScript PUT = new RedisScript("""
            local previous = redis.call('HGET', KEYS[2], ARGV[1])
            if previous then
                redis.call('ZREM', KEYS[1], previous .. '\\0' .. ARGV[1])
            end
            redis.call('ZADD', KEYS[1], 0, ARGV[2] .. '\\0' .. ARGV[1])
            redis.call('HSET', KEYS[2], ARGV[1], ARGV[2])
            """);

    private static final RedisScript RANK = new RedisScript("""
            local score = redis.call('HGET', KEYS[2], ARGV[1])
            if not score then
                return false
            end
            return redis.call('ZREVRANK', KEYS[1], score .. '\\0' .. ARGV[1])
            """);

    private static final RedisScript REMOVE = new RedisScript("""
            local score = redis.call('HGET', KEYS[2], ARGV[1])
            if not score then
                return 0
            end
            redis.call('ZREM', KEYS[1], score .. '\\0' .. ARGV[1])
            redis.call('HDEL', KEYS[2], ARGV[1])
            return 1
            """);

    private final UnifiedJedis jedis;
    private final String rankingKey;
    private final String scoresKey;
    private final List<String> keys;

    RedisLexicographicStorage(UnifiedJedis jedis, String rankingKey, String scoresKey) {
        this.jedis = jedis;
        this.rankingKey = rankingKey;
        this.scoresKey = scoresKey;
        this.keys = List.of(rankingKey, scoresKey);
    }

    @Override
    public void put(String key, String score) {
        PUT.eval(jedis, keys, List.of(key, score));
    }

    @Override
    public Optional<String> score(String key) {
        return Optional.ofNullable(jedis.hget(scoresKey, key));
    }

    @Override
    public OptionalLong rank(String key) {
        Long rank = (Long) RANK.eval(jedis, keys, List.of(key));
        return rank == null ? OptionalLong.empty() : OptionalLong.of(rank + 1);
    }

    @Override
    public List<StoredEntry<String>> top(int n) {
        if (n == 0) {
            return List.of();
        }
        return jedis.zrange(rankingKey, ZRangeParams.zrangeParams(0, n - 1L).rev()).stream()
                .map(RedisLexicographicStorage::entry)
                .toList();
    }

    @Override
    public boolean remove(String key) {
        return (Long) REMOVE.eval(jedis, keys, List.of(key)) > 0;
    }

    @Override
    public long size() {
        return jedis.hlen(scoresKey);
    }

    static StoredEntry<String> entry(String member) {
        int separator = member.indexOf('\0');
        return new StoredEntry<>(member.substring(separator + 1), member.substring(0, separator));
    }
}
