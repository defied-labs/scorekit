package dev.defied.scorekit.storage.redis;

import dev.defied.scorekit.storage.StoredEntry;
import dev.defied.scorekit.storage.VersionedEntry;
import dev.defied.scorekit.storage.cache.CacheColdException;
import dev.defied.scorekit.storage.cache.CacheState;
import dev.defied.scorekit.storage.cache.CacheStorage;
import redis.clients.jedis.AbstractPipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.exceptions.JedisDataException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

abstract class RedisCacheStorage<S> implements CacheStorage<S> {

    static final String GUARD = """
            if redis.call('HEXISTS', KEYS[1], 'warm') == 0 then
                return redis.error_reply('SCOREKIT_COLD')
            end
            """;

    static final String ACCEPT = """
            if redis.call('HEXISTS', KEYS[1], 'warm') == 0 and redis.call('EXISTS', KEYS[#KEYS]) == 0 then
                return 0
            end
            """;

    private static final RedisScript EVICT = new RedisScript("""
            if redis.call('HEXISTS', KEYS[1], 'warm') == 0 then
                return 0
            end
            if tonumber(redis.call('HGET', KEYS[1], 'touched') or '0') >= tonumber(ARGV[1]) then
                return 0
            end
            redis.call('HDEL', KEYS[1], 'warm', 'swept', 'touched')
            for i = 2, #KEYS - 1 do
                redis.call('UNLINK', KEYS[i])
            end
            return 1
            """);

    private static final String COLD = "SCOREKIT_COLD";
    private static final String TOMBSTONE_TTL_MILLIS = "300000";

    private final UnifiedJedis jedis;
    private final List<String> keys;
    private final Scripts scripts;
    private final RedisCacheState state;

    RedisCacheStorage(UnifiedJedis jedis, List<String> keys, Scripts scripts, RedisCacheState state) {
        this.jedis = jedis;
        this.keys = keys;
        this.scripts = scripts;
        this.state = state;
    }

    abstract String encode(S score);

    abstract S decode(String encoded);

    abstract List<StoredEntry<S>> entries(List<?> reply);

    @Override
    public Optional<S> score(String key) {
        return Optional.ofNullable((String) read(scripts.score(), key)).map(this::decode);
    }

    @Override
    public OptionalLong rank(String key) {
        Long rank = (Long) read(scripts.rank(), key);
        return rank == null ? OptionalLong.empty() : OptionalLong.of(rank + 1);
    }

    @Override
    public List<StoredEntry<S>> top(int n) {
        if (n == 0) {
            return List.of();
        }
        return entries((List<?>) read(scripts.top(), String.valueOf(n - 1L)));
    }

    @Override
    public long size() {
        return (Long) read(scripts.size());
    }

    @Override
    public void putIfNewer(String key, S score, long version) {
        scripts.put().eval(jedis, keys, putArgs(key, score, version));
    }

    @Override
    public void removeIfNewer(String key, long version) {
        scripts.remove().eval(jedis, keys, removeArgs(key, version));
    }

    @Override
    public void applyAll(List<VersionedEntry<S>> entries) {
        if (entries.isEmpty()) {
            return;
        }
        scripts.put().load(jedis, keys.getFirst());
        scripts.remove().load(jedis, keys.getFirst());
        try (AbstractPipeline pipeline = jedis.pipelined()) {
            List<Response<Object>> responses = new ArrayList<>(entries.size());
            for (VersionedEntry<S> entry : entries) {
                responses.add(entry.isRemoved()
                        ? scripts.remove().eval(pipeline, keys, removeArgs(entry.key(), entry.version()))
                        : scripts.put().eval(pipeline, keys, putArgs(entry.key(), entry.score(), entry.version())));
            }
            pipeline.sync();
            responses.forEach(Response::get);
        }
    }

    @Override
    public void clear() {
        jedis.unlink(keys.subList(1, keys.size() - 1).toArray(String[]::new));
    }

    @Override
    public boolean evictIfIdle(long idleBeforeMillis) {
        return (Long) EVICT.eval(jedis, keys, List.of(String.valueOf(idleBeforeMillis))) == 1;
    }

    @Override
    public CacheState state() {
        return state;
    }

    private List<String> putArgs(String key, S score, long version) {
        return List.of(key, encode(score), String.valueOf(version));
    }

    private List<String> removeArgs(String key, long version) {
        return List.of(key, String.valueOf(version), TOMBSTONE_TTL_MILLIS);
    }

    private Object read(RedisScript script, String... args) {
        try {
            return script.eval(jedis, keys, List.of(args));
        } catch (JedisDataException e) {
            if (e.getMessage() != null && e.getMessage().contains(COLD)) {
                throw new CacheColdException();
            }
            throw e;
        }
    }

    record Scripts(RedisScript put, RedisScript remove, RedisScript score, RedisScript rank, RedisScript top, RedisScript size) {
    }
}
