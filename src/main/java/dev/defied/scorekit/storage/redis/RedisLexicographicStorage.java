package dev.defied.scorekit.storage.redis;

import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.ScanPage;
import dev.defied.scorekit.storage.StoredEntry;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

final class RedisLexicographicStorage implements LeaderboardStorage<String> {

    private static final String PUT = """
            local previous = redis.call('HGET', KEYS[2], ARGV[1])
            if previous then
                redis.call('ZREM', KEYS[1], previous .. '\\0' .. ARGV[1])
            end
            redis.call('ZADD', KEYS[1], 0, ARGV[2] .. '\\0' .. ARGV[1])
            redis.call('HSET', KEYS[2], ARGV[1], ARGV[2])
            """;

    private static final String RANK = """
            local score = redis.call('HGET', KEYS[2], ARGV[1])
            if not score then
                return false
            end
            return redis.call('ZREVRANK', KEYS[1], score .. '\\0' .. ARGV[1])
            """;

    private static final String REMOVE = """
            local score = redis.call('HGET', KEYS[2], ARGV[1])
            if not score then
                return 0
            end
            redis.call('ZREM', KEYS[1], score .. '\\0' .. ARGV[1])
            redis.call('HDEL', KEYS[2], ARGV[1])
            return 1
            """;

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
        jedis.eval(PUT, keys, List.of(key, score));
    }

    @Override
    public Optional<String> score(String key) {
        return Optional.ofNullable(jedis.hget(scoresKey, key));
    }

    @Override
    public OptionalLong rank(String key) {
        Long rank = (Long) jedis.eval(RANK, keys, List.of(key));
        return rank == null ? OptionalLong.empty() : OptionalLong.of(rank + 1);
    }

    @Override
    public List<StoredEntry<String>> top(int n) {
        if (n == 0) {
            return List.of();
        }
        return jedis.zrevrange(rankingKey, 0, n - 1L).stream()
                .map(RedisLexicographicStorage::entry)
                .toList();
    }

    @Override
    public boolean remove(String key) {
        return (Long) jedis.eval(REMOVE, keys, List.of(key)) > 0;
    }

    @Override
    public long size() {
        return jedis.hlen(scoresKey);
    }

    @Override
    public ScanPage<String> scan(String cursor, int count) {
        ScanResult<Map.Entry<String, String>> result = jedis.hscan(scoresKey, cursor == null ? ScanParams.SCAN_POINTER_START : cursor, new ScanParams().count(count));
        List<StoredEntry<String>> entries = result.getResult().stream()
                .map(entry -> new StoredEntry<>(entry.getKey(), entry.getValue()))
                .toList();
        return new ScanPage<>(entries, result.isCompleteIteration() ? null : result.getCursor());
    }

    @Override
    public void clear() {
        jedis.unlink(rankingKey, scoresKey);
    }

    private static StoredEntry<String> entry(String member) {
        int separator = member.indexOf('\0');
        return new StoredEntry<>(member.substring(separator + 1), member.substring(0, separator));
    }
}
