package dev.defied.scorekit.storage.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.VersionedStorage;
import dev.defied.scorekit.storage.VersionedStorageFactory;
import org.bson.Document;

import java.util.List;
import java.util.Objects;

public final class MongoStorageFactory implements VersionedStorageFactory {

    private static final List<IndexModel> INDEXES = List.of(
            new IndexModel(Indexes.ascending("provider", "leaderboard", "key"), new IndexOptions().unique(true)),
            new IndexModel(Indexes.ascending("provider", "leaderboard", "removed", "score", "key")),
            new IndexModel(Indexes.ascending("provider", "leaderboard", "version")));

    private final MongoCollection<Document> numeric;
    private final MongoCollection<Document> text;

    public MongoStorageFactory(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        numeric = indexed(database.getCollection("scorekit_numeric_scores"));
        text = indexed(database.getCollection("scorekit_text_scores"));
    }

    @Override
    public <S> VersionedStorage<S> create(LeaderboardId id, ScoreType<S> type) {
        return new MongoStorage<>(type == ScoreType.NUMERIC ? numeric : text, id);
    }

    private static MongoCollection<Document> indexed(MongoCollection<Document> collection) {
        collection.createIndexes(INDEXES);
        return collection;
    }
}
