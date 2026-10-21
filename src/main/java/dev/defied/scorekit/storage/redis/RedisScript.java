package dev.defied.scorekit.storage.redis;

import redis.clients.jedis.AbstractPipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.exceptions.JedisNoScriptException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

final class RedisScript {

    private final String source;
    private final String sha;

    RedisScript(String source) {
        this.source = source;
        try {
            sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    Object eval(UnifiedJedis jedis, List<String> keys, List<String> args) {
        try {
            return jedis.evalsha(sha, keys, args);
        } catch (JedisNoScriptException e) {
            return jedis.eval(source, keys, args);
        }
    }

    Response<Object> eval(AbstractPipeline pipeline, List<String> keys, List<String> args) {
        return pipeline.evalsha(sha, keys, args);
    }

    void load(UnifiedJedis jedis, String sampleKey) {
        jedis.scriptLoad(source, sampleKey);
    }
}
