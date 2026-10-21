package dev.defied.scorekit.storage.redis;

import dev.defied.scorekit.storage.cache.CacheState;
import redis.clients.jedis.UnifiedJedis;

import java.util.List;
import java.util.OptionalLong;

final class RedisCacheState implements CacheState {

    private static final String LEASE_MILLIS = "30000";

    private static final RedisScript MARK_DIRTY = new RedisScript("""
            if redis.call('HEXISTS', KEYS[1], 'warm') == 1 then
                return 0
            end
            redis.call('SADD', KEYS[2], ARGV[1])
            return 1
            """);

    private static final RedisScript BEGIN = new RedisScript("""
            if redis.call('HEXISTS', KEYS[1], 'warm') == 1 then
                return false
            end
            local epoch = tostring(redis.call('HINCRBY', KEYS[1], 'epoch', 0) + 1)
            if not redis.call('SET', KEYS[3], epoch, 'NX', 'PX', ARGV[1]) then
                return false
            end
            redis.call('HSET', KEYS[1], 'epoch', epoch)
            redis.call('DEL', KEYS[2])
            return tonumber(epoch)
            """);

    private static final RedisScript RENEW = new RedisScript("""
            if redis.call('HGET', KEYS[1], 'epoch') ~= ARGV[1] or redis.call('GET', KEYS[3]) ~= ARGV[1] then
                return 0
            end
            redis.call('PEXPIRE', KEYS[3], ARGV[2])
            return 1
            """);

    private static final RedisScript COMPLETE = new RedisScript("""
            if redis.call('HGET', KEYS[1], 'epoch') ~= ARGV[1] or redis.call('GET', KEYS[3]) ~= ARGV[1] then
                return 0
            end
            if redis.call('SCARD', KEYS[2]) > 0 then
                return 0
            end
            redis.call('HSET', KEYS[1], 'warm', '1')
            redis.call('DEL', KEYS[3])
            return 1
            """);

    private static final RedisScript ABORT = new RedisScript("""
            if redis.call('GET', KEYS[3]) == ARGV[1] then
                redis.call('DEL', KEYS[3])
            end
            return 0
            """);

    private static final RedisScript INVALIDATE = new RedisScript("""
            redis.call('HDEL', KEYS[1], 'warm')
            redis.call('HINCRBY', KEYS[1], 'epoch', 1)
            return 0
            """);

    private final UnifiedJedis jedis;
    private final String stateKey;
    private final String dirtyKey;
    private final List<String> keys;

    RedisCacheState(UnifiedJedis jedis, String stateKey, String dirtyKey, String lockKey) {
        this.jedis = jedis;
        this.stateKey = stateKey;
        this.dirtyKey = dirtyKey;
        this.keys = List.of(stateKey, dirtyKey, lockKey);
    }

    @Override
    public boolean isWarm() {
        return jedis.hexists(stateKey, "warm");
    }

    @Override
    public boolean markDirtyUnlessWarm(String key) {
        return eval(MARK_DIRTY, key);
    }

    @Override
    public OptionalLong beginWarmUp() {
        Long epoch = (Long) BEGIN.eval(jedis, keys, List.of(LEASE_MILLIS));
        return epoch == null ? OptionalLong.empty() : OptionalLong.of(epoch);
    }

    @Override
    public boolean renewWarmUp(long epoch) {
        return eval(RENEW, String.valueOf(epoch), LEASE_MILLIS);
    }

    @Override
    public List<String> pollDirty(int count) {
        return List.copyOf(jedis.spop(dirtyKey, count));
    }

    @Override
    public boolean completeWarmUp(long epoch) {
        return eval(COMPLETE, String.valueOf(epoch));
    }

    @Override
    public void abortWarmUp(long epoch) {
        eval(ABORT, String.valueOf(epoch));
    }

    @Override
    public void invalidate() {
        INVALIDATE.eval(jedis, keys, List.of());
    }

    private boolean eval(RedisScript script, String... args) {
        return (Long) script.eval(jedis, keys, List.of(args)) == 1;
    }
}
