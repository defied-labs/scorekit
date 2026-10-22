package dev.defied.scorekit.storage.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.VersionedStorage;
import dev.defied.scorekit.storage.VersionedStorageContract;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class MongoStorageTest extends VersionedStorageContract {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    private static MongoClient client;
    private static MongoStorageFactory factory;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MONGO.getConnectionString());
        factory = new MongoStorageFactory(client.getDatabase("scorekit"));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @Override
    protected <S> VersionedStorage<S> create(ScoreType<S> type) {
        return factory.create(new LeaderboardId("test", UUID.randomUUID().toString()), type);
    }

    @Test
    void dataIsSharedBetweenStorageInstances() {
        LeaderboardId id = new LeaderboardId("test", UUID.randomUUID().toString());
        factory.create(id, ScoreType.NUMERIC).put("a", 4.0);

        LeaderboardStorage<Double> other = new MongoStorageFactory(client.getDatabase("scorekit")).create(id, ScoreType.NUMERIC);

        assertEquals(Optional.of(4.0), other.score("a"));
    }

    @Test
    void scoresThatLookLikeFieldPathsAreStoredVerbatim() {
        VersionedStorage<String> lex = create(ScoreType.LEXICOGRAPHIC);

        lex.put("a", "$version");

        assertEquals(Optional.of("$version"), lex.score("a"));
    }

    @Test
    void leaderboardsWithSameNameInDifferentProvidersAreIsolated() {
        String name = UUID.randomUUID().toString();
        factory.create(new LeaderboardId("first", name), ScoreType.NUMERIC).put("a", 1.0);

        assertEquals(0, factory.create(new LeaderboardId("second", name), ScoreType.NUMERIC).size());
    }
}
