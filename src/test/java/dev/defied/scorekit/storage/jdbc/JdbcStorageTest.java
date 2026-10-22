package dev.defied.scorekit.storage.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.VersionedStorage;
import dev.defied.scorekit.storage.VersionedStorageContract;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

abstract class JdbcStorageTest extends VersionedStorageContract {

    private static HikariDataSource dataSource;
    private static JdbcStorageFactory factory;

    static void connect(JdbcDatabaseContainer<?> database) {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(database.getJdbcUrl());
        dataSource.setUsername(database.getUsername());
        dataSource.setPassword(database.getPassword());
        factory = new JdbcStorageFactory(dataSource);
        factory.createSchema();
    }

    @AfterAll
    static void disconnect() {
        dataSource.close();
    }

    @Override
    protected <S> VersionedStorage<S> create(ScoreType<S> type) {
        return factory.create(new LeaderboardId("test", UUID.randomUUID().toString()), type);
    }

    @Test
    void schemaCanBeCreatedAgain() {
        factory.createSchema();
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

    @Test
    void keysAndScoresDifferingByTrailingSpaceAreDistinct() {
        VersionedStorage<String> lex = create(ScoreType.LEXICOGRAPHIC);
        lex.put("a", "x ");
        lex.put("a ", "x");

        assertEquals(Optional.of("x "), lex.score("a"));
        assertEquals(Optional.of("x"), lex.score("a "));
        assertEquals(2, lex.size());
        assertEquals("a", lex.top(1).getFirst().key());
    }

    @Test
    void infiniteScoresAreRejectedWhenUnsupported() {
        assumeFalse(supportsInfiniteScores());
        VersionedStorage<Double> storage = create(ScoreType.NUMERIC);

        assertThrows(IllegalArgumentException.class, () -> storage.put("a", Double.POSITIVE_INFINITY));
        assertEquals(0, storage.size());
    }
}