package dev.defied.scorekit.storage.redis;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.cache.CacheStorage;
import dev.defied.scorekit.storage.cache.CacheStorageContract;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.RedisClient;

import java.util.UUID;

@Testcontainers
class RedisCacheTest extends CacheStorageContract {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    private static RedisClient jedis;
    private static RedisStorageFactory factory;

    @BeforeAll
    static void connect() {
        jedis = RedisClient.create(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory = new RedisStorageFactory(jedis);
    }

    @AfterAll
    static void disconnect() {
        jedis.close();
    }

    @Override
    protected <S> CacheStorage<S> createCache(ScoreType<S> type) {
        return factory.createCache(new LeaderboardId("test", UUID.randomUUID().toString()), type);
    }
}
