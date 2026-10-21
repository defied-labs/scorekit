package dev.defied.scorekit.storage.redis;

import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.cache.CacheState;
import dev.defied.scorekit.storage.cache.CacheStateContract;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.RedisClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class RedisCacheStateTest extends CacheStateContract {

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
    protected CacheState create() {
        return factory.cacheState(new LeaderboardId("test", UUID.randomUUID().toString()));
    }

    @Test
    void stateIsSharedBetweenInstances() {
        LeaderboardId id = new LeaderboardId("test", UUID.randomUUID().toString());
        CacheState first = factory.cacheState(id);
        long epoch = first.beginWarmUp().orElseThrow();
        first.completeWarmUp(epoch);

        assertTrue(new RedisStorageFactory(jedis).cacheState(id).isWarm());
    }
}
