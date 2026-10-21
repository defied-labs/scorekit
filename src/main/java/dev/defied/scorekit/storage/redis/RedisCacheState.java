package dev.defied.scorekit.storage.redis;

import dev.defied.scorekit.storage.cache.CacheState;
import redis.clients.jedis.UnifiedJedis;

import java.util.List;
import java.util.OptionalLong;

final class RedisCacheState implements CacheState {

    private static final String LEASE_MILLIS = "30000";

    private static final RedisScript BEGIN = new RedisScript("""
            if redis.call('HEXISTS', KEYS[1], 'warm') == 1 then
                return false
            end
            local epoch = tostring(redis.call('HINCRBY', KEYS[1], 'epoch', 1))
            if not redis.call('SET', KEYS[2], epoch, 'NX', 'PX', ARGV[1]) then
                return false
            end
            return tonumber(epoch)
            """);

    private static final RedisScript RENEW = new RedisScript("""
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then
                return 0
            end
            redis.call('PEXPIRE', KEYS[2], ARGV[2])
            return 1
            """);

    private static final RedisScript COMPLETE = new RedisScript("""
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then
                return 0
            end
            redis.call('HSET', KEYS[1], 'warm', '1', 'swept', ARGV[2])
            redis.call('DEL', KEYS[2])
            return 1
            """);

    private static final RedisScript ABORT = new RedisScript("""
            if redis.call('GET', KEYS[2]) == ARGV[1] then
                redis.call('DEL', KEYS[2])
            end
            return 0
            """);

    private static final RedisScript TRY_SWEEP = new RedisScript("""
            if redis.call('HEXISTS', KEYS[1], 'warm') == 0 then
                return false
            end
            if not redis.call('SET', KEYS[3], '1', 'NX', 'PX', ARGV[1]) then
                return false
            end
            return redis.call('HGET', KEYS[1], 'swept')
            """);

    private static final RedisScript COMPLETE_SWEEP = new RedisScript("""
            if redis.call('HEXISTS', KEYS[1], 'warm') == 1 then
                redis.call('HSET', KEYS[1], 'swept', ARGV[1])
            end
            return 0
            """);

    private final UnifiedJedis jedis;
    private final String stateKey;
    private final List<String> keys;

    RedisCacheState(UnifiedJedis jedis, String stateKey, String lockKey, String sweepKey) {
        this.jedis = jedis;
        this.stateKey = stateKey;
        this.keys = List.of(stateKey, lockKey, sweepKey);
    }

    @Override
    public boolean isWarm() {
        return jedis.hexists(stateKey, "warm");
    }

    @Override
    public OptionalLong beginWarmUp() {
        Long epoch = (Long) BEGIN.eval(jedis, keys, List.of(LEASE_MILLIS));
        return epoch == null ? OptionalLong.empty() : OptionalLong.of(epoch);
    }

    @Override
    public boolean renewWarmUp(long epoch) {
        return (Long) RENEW.eval(jedis, keys, List.of(String.valueOf(epoch), LEASE_MILLIS)) == 1;
    }

    @Override
    public boolean completeWarmUp(long epoch, long watermark) {
        return (Long) COMPLETE.eval(jedis, keys, List.of(String.valueOf(epoch), String.valueOf(watermark))) == 1;
    }

    @Override
    public void abortWarmUp(long epoch) {
        ABORT.eval(jedis, keys, List.of(String.valueOf(epoch)));
    }

    @Override
    public OptionalLong tryStartSweep(long intervalMillis) {
        String watermark = (String) TRY_SWEEP.eval(jedis, keys, List.of(String.valueOf(intervalMillis)));
        return watermark == null ? OptionalLong.empty() : OptionalLong.of(Long.parseLong(watermark));
    }

    @Override
    public void completeSweep(long watermark) {
        COMPLETE_SWEEP.eval(jedis, keys, List.of(String.valueOf(watermark)));
    }
}
