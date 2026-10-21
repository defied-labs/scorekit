package dev.defied.scorekit.storage.redis;

import dev.defied.scorekit.storage.StoredEntry;
import redis.clients.jedis.UnifiedJedis;

import java.util.List;

final class RedisLexicographicCache extends RedisCacheStorage<String> {

    private static final Scripts SCRIPTS = new Scripts(
            new RedisScript("""
                    local current = redis.call('HGET', KEYS[4], ARGV[1])
                    if current and tonumber(current) >= tonumber(ARGV[3]) then
                        return 0
                    end
                    local previous = redis.call('HGET', KEYS[3], ARGV[1])
                    if previous then
                        redis.call('ZREM', KEYS[2], previous .. '\\0' .. ARGV[1])
                    end
                    redis.call('ZADD', KEYS[2], 0, ARGV[2] .. '\\0' .. ARGV[1])
                    redis.call('HSET', KEYS[3], ARGV[1], ARGV[2])
                    redis.call('HSET', KEYS[4], ARGV[1], ARGV[3])
                    redis.call('HPERSIST', KEYS[4], 'FIELDS', 1, ARGV[1])
                    return 1
                    """),
            new RedisScript("""
                    local current = redis.call('HGET', KEYS[4], ARGV[1])
                    if current and tonumber(current) >= tonumber(ARGV[2]) then
                        return 0
                    end
                    local previous = redis.call('HGET', KEYS[3], ARGV[1])
                    if previous then
                        redis.call('ZREM', KEYS[2], previous .. '\\0' .. ARGV[1])
                        redis.call('HDEL', KEYS[3], ARGV[1])
                    end
                    redis.call('HSET', KEYS[4], ARGV[1], ARGV[2])
                    redis.call('HPEXPIRE', KEYS[4], ARGV[3], 'FIELDS', 1, ARGV[1])
                    return 1
                    """),
            new RedisScript(GUARD + "return redis.call('HGET', KEYS[3], ARGV[1])"),
            new RedisScript(GUARD + """
                    local score = redis.call('HGET', KEYS[3], ARGV[1])
                    if not score then
                        return false
                    end
                    return redis.call('ZREVRANK', KEYS[2], score .. '\\0' .. ARGV[1])
                    """),
            new RedisScript(GUARD + "return redis.call('ZREVRANGE', KEYS[2], 0, ARGV[1])"),
            new RedisScript(GUARD + "return redis.call('HLEN', KEYS[3])"));

    RedisLexicographicCache(UnifiedJedis jedis, String prefix, RedisCacheState state) {
        super(jedis, List.of(prefix + "state", prefix + "lex", prefix + "scores", prefix + "versions"), SCRIPTS, state);
    }

    @Override
    String encode(String score) {
        return score;
    }

    @Override
    String decode(String encoded) {
        return encoded;
    }

    @Override
    List<StoredEntry<String>> entries(List<?> reply) {
        return reply.stream().map(member -> RedisLexicographicStorage.entry((String) member)).toList();
    }
}
