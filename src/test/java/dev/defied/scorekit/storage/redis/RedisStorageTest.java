package dev.defied.scorekit.storage.redis;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.LeaderboardStorageContract;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.RedisClient;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class RedisStorageTest extends LeaderboardStorageContract {

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
    protected <S> LeaderboardStorage<S> create(ScoreType<S> type) {
        return factory.create(new LeaderboardId("test", UUID.randomUUID().toString()), type);
    }

    @Test
    void dataIsSharedBetweenStorageInstances() {
        LeaderboardId id = new LeaderboardId("test", UUID.randomUUID().toString());
        factory.create(id, ScoreType.LEXICOGRAPHIC).put("a", "first");

        LeaderboardStorage<String> other = new RedisStorageFactory(jedis).create(id, ScoreType.LEXICOGRAPHIC);

        assertEquals(Optional.of("first"), other.score("a"));
    }

    @Test
    void providerNameWithColonIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> factory.create(new LeaderboardId("a:b", "c"), ScoreType.NUMERIC));
    }
}
