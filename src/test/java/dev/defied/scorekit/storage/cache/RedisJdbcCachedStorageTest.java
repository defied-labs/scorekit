package dev.defied.scorekit.storage.cache;

import com.zaxxer.hikari.HikariDataSource;
import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.LeaderboardStorageContract;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
    static void connect() throws IOException, SQLException {
        jedis = RedisClient.create(REDIS.getHost(), REDIS.getMappedPort(6379));
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        try (InputStream schema = JdbcStorageFactory.class.getResourceAsStream("postgresql.sql");
             Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(new String(schema.readAllBytes(), StandardCharsets.UTF_8));
        }
        redisFactory = new RedisStorageFactory(jedis);
        jdbcFactory = new JdbcStorageFactory(dataSource);
        factory = new CachedStorageFactory(redisFactory, jdbcFactory);
    }

    @AfterAll
    static void disconnect() {
        jedis.close();
        dataSource.close();
    }

    @Override
    protected <S> LeaderboardStorage<S> create(ScoreType<S> type) {
        return factory.create(new LeaderboardId("test", UUID.randomUUID().toString()), type);
    }

    @Test
    void cacheConvergesWithDatabaseUnderConcurrentWrites() throws InterruptedException {
        LeaderboardId id = new LeaderboardId("test", UUID.randomUUID().toString());
        LeaderboardStorage<Double> cached = factory.create(id, ScoreType.NUMERIC);
        CacheState state = redisFactory.cacheState(id);
        for (int i = 0; i < 1_200; i++) {
            cached.put("seed" + i, (double) i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(6);
        for (int thread = 0; thread < 6; thread++) {
            executor.execute(() -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i = 0; i < 400; i++) {
                    String key = (random.nextBoolean() ? "seed" : "k") + random.nextInt(100);
                    switch (random.nextInt(3)) {
                        case 0 -> cached.remove(key);
                        case 1 -> cached.put(key, (double) random.nextInt(1_000));
                        default -> cached.top(5);
                    }
                }
            });
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!state.isWarm()) {
            if (System.nanoTime() > deadline) {
                fail("Cache did not warm up");
            }
            cached.size();
            Thread.sleep(20);
        }
        assertEquals(jdbcFactory.create(id, ScoreType.NUMERIC).top(Integer.MAX_VALUE),
                redisFactory.create(id, ScoreType.NUMERIC).top(Integer.MAX_VALUE));
    }
}
