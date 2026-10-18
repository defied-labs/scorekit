package dev.defied.scorekit.storage.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.LeaderboardStorageContract;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class JdbcStorageTest extends LeaderboardStorageContract {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static HikariDataSource dataSource;
    private static JdbcStorageFactory factory;

    @BeforeAll
    static void connect() throws IOException, SQLException {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        try (InputStream schema = JdbcStorageFactory.class.getResourceAsStream("postgresql.sql");
             Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(new String(schema.readAllBytes(), StandardCharsets.UTF_8));
        }
        factory = new JdbcStorageFactory(dataSource);
    }

    @AfterAll
    static void disconnect() {
        dataSource.close();
    }

    @Override
    protected <S> LeaderboardStorage<S> create(ScoreType<S> type) {
        return factory.create(new LeaderboardId("test", UUID.randomUUID().toString()), type);
    }

    @Test
    void dataIsSharedBetweenStorageInstances() {
        LeaderboardId id = new LeaderboardId("test", UUID.randomUUID().toString());
        factory.create(id, ScoreType.NUMERIC).put("a", 4.0);

        LeaderboardStorage<Double> other = new JdbcStorageFactory(dataSource).create(id, ScoreType.NUMERIC);

        assertEquals(Optional.of(4.0), other.score("a"));
    }

    @Test
    void leaderboardsWithSameNameInDifferentProvidersAreIsolated() {
        String name = UUID.randomUUID().toString();
        factory.create(new LeaderboardId("first", name), ScoreType.NUMERIC).put("a", 1.0);

        assertEquals(0, factory.create(new LeaderboardId("second", name), ScoreType.NUMERIC).size());
    }
}
