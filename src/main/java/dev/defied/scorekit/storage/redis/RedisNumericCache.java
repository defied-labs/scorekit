package dev.defied.scorekit.storage.redis;

import dev.defied.scorekit.storage.StoredEntry;
import redis.clients.jedis.UnifiedJedis;

import java.util.ArrayList;
import java.util.List;

final class RedisNumericCache extends RedisCacheStorage<Double> {

    private static final Scripts SCRIPTS = new Scripts(
            new RedisScript(ACCEPT + """
                    local current = redis.call('HGET', KEYS[3], ARGV[1])
                    if current and tonumber(current) >= tonumber(ARGV[3]) then
                        return 0
                    end
                    redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1])
                    redis.call('HSET', KEYS[3], ARGV[1], ARGV[3])
                    redis.call('HPERSIST', KEYS[3], 'FIELDS', 1, ARGV[1])
                    return 1
                    """),
            new RedisScript(ACCEPT + """
                    local current = redis.call('HGET', KEYS[3], ARGV[1])
                    if current and tonumber(current) >= tonumber(ARGV[2]) then
                        return 0
                    end
                    redis.call('ZREM', KEYS[2], ARGV[1])
                    redis.call('HSET', KEYS[3], ARGV[1], ARGV[2])
                    redis.call('HPEXPIRE', KEYS[3], ARGV[3], 'FIELDS', 1, ARGV[1])
                    return 1
                    """),
            new RedisScript(GUARD + "return redis.call('ZSCORE', KEYS[2], ARGV[1])"),
            new RedisScript(GUARD + "return redis.call('ZREVRANK', KEYS[2], ARGV[1])"),
            new RedisScript(GUARD + "return redis.call('ZREVRANGE', KEYS[2], 0, ARGV[1], 'WITHSCORES')"),
            new RedisScript(GUARD + "return redis.call('ZCARD', KEYS[2])"));

    RedisNumericCache(UnifiedJedis jedis, String prefix, RedisCacheState state) {
        super(jedis, List.of(prefix + "state", prefix + "ranking", prefix + "versions", prefix + "lock"), SCRIPTS, state);
    }

    @Override
    String encode(Double score) {
        if (score == Double.POSITIVE_INFINITY) {
            return "+inf";
        }
        if (score == Double.NEGATIVE_INFINITY) {
            return "-inf";
        }
        return Double.toString(score);
    }

    @Override
    Double decode(String encoded) {
        return switch (encoded) {
            case "inf", "+inf" -> Double.POSITIVE_INFINITY;
            case "-inf" -> Double.NEGATIVE_INFINITY;
            default -> Double.valueOf(encoded);
        };
    }

    @Override
    List<StoredEntry<Double>> entries(List<?> reply) {
        List<StoredEntry<Double>> entries = new ArrayList<>(reply.size() / 2);
        for (int i = 0; i < reply.size(); i += 2) {
            entries.add(new StoredEntry<>((String) reply.get(i), decode((String) reply.get(i + 1))));
        }
        return entries;
    }
}
