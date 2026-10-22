package dev.defied.scorekit.storage.cache;

import com.zaxxer.hikari.HikariDataSource;
import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.LeaderboardStorageContract;
import dev.defied.scorekit.storage.Parallel;
import dev.defied.scorekit.storage.jdbc.JdbcStorageFactory;
import dev.defied.scorekit.storage.redis.RedisStorageFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import redis.clients.jedis.RedisClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class RedisJdbcCachedStorageTest extends LeaderboardStorageContract {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static RedisClient jedis;
    private static HikariDataSource dataSource;
    private static RedisStorageFactory redisFactory;
    private static JdbcStorageFactory jdbcFactory;
    private static CachedStorageFactory factory;

    @BeforeAll
    static void connect() {
        jedis = RedisClient.create(REDIS.getHost(), REDIS.getMappedPort(6379));
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        redisFactory = new RedisStorageFactory(jedis);
        jdbcFactory = new JdbcStorageFactory(dataSource);
        jdbcFactory.createSchema();
        factory = new CachedStorageFactory(redisFactory, jdbcFactory);
    }

    @AfterAll
    static void disconnect() {
        factory.close();
        jedis.close();
        dataSource.close();
    }

    @Override
    protected <S> LeaderboardStorage<S> create(ScoreType<S> type) {
        return factory.create(new LeaderboardId("test", UUID.randomUUID().toString()), type);
    }

    @Test
    void cacheConvergesWithDatabaseUnderConcurrentWritersFromSeveralInstances() throws Exception {
        LeaderboardId id = new LeaderboardId("test", UUID.randomUUID().toString());
        List<LeaderboardStorage<Double>> instances = List.of(
                factory.create(id, ScoreType.NUMERIC),
                new CachedStorageFactory(new RedisStorageFactory(jedis), new JdbcStorageFactory(dataSource)).create(id, ScoreType.NUMERIC));
        for (int i = 0; i < 1_200; i++) {
            instances.getFirst().put("seed" + i, (double) i);
        }

        Parallel.run(6, () -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 400; i++) {
                LeaderboardStorage<Double> instance = instances.get(random.nextInt(instances.size()));
                String key = (random.nextBoolean() ? "seed" : "k") + random.nextInt(30);
                switch (random.nextInt(3)) {
                    case 0 -> instance.remove(key);
                    case 1 -> instance.put(key, (double) random.nextInt(1_000));
                    default -> instance.top(5);
                }
            }
        });

        CacheStorage<Double> cache = redisFactory.createCache(id, ScoreType.NUMERIC);
        CachedStorageTest.awaitWarm(instances.getFirst(), cache.state());
        assertEquals(jdbcFactory.create(id, ScoreType.NUMERIC).top(Integer.MAX_VALUE), cache.top(Integer.MAX_VALUE));
    }

    @Test
    void sweepRepairsWritesThatNeverReachedRedis() {
        LeaderboardId id = new LeaderboardId("test", UUID.randomUUID().toString());
        CachedStorage<Double> cached = (CachedStorage<Double>) factory.create(id, ScoreType.NUMERIC);
        cached.put("gone", 1.0);
        cached.warmUp();

        jdbcFactory.create(id, ScoreType.NUMERIC).put("lost", 7.0);
        jdbcFactory.create(id, ScoreType.NUMERIC).remove("gone");
        cached.sweep();

        CacheStorage<Double> cache = redisFactory.createCache(id, ScoreType.NUMERIC);
        assertEquals(Optional.of(7.0), cache.score("lost"));
        assertEquals(Optional.empty(), cache.score("gone"));
    }
}
