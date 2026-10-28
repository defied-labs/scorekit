package dev.defied.scorekit.storage.mongo;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.ScanPage;
import dev.defied.scorekit.storage.StorageException;
import dev.defied.scorekit.storage.StoredEntry;
import dev.defied.scorekit.storage.VersionedEntry;
import dev.defied.scorekit.storage.VersionedStorage;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Filters.or;

final class MongoStorage<S> implements VersionedStorage<S> {

    private static final int PUT_ATTEMPTS = 3;
    private static final int DUPLICATE_KEY = 11000;
    private static final Bson DESCENDING = Sorts.descending("score", "key");
    private static final Bson BY_KEY = Sorts.ascending("key");
    private static final Bson ENTRY_FIELDS = Projections.include("key", "score", "version", "removed");
    private static final FindOneAndUpdateOptions RETURN_VERSION = new FindOneAndUpdateOptions()
            .projection(Projections.include("version"))
            .returnDocument(ReturnDocument.AFTER);
    private static final FindOneAndUpdateOptions UPSERT_RETURN_VERSION = new FindOneAndUpdateOptions()
            .projection(Projections.include("version"))
            .returnDocument(ReturnDocument.AFTER)
            .upsert(true);

    private final MongoCollection<Document> collection;
    private final LeaderboardId id;
    private final Bson scope;
    private final Bson live;

    MongoStorage(MongoCollection<Document> collection, LeaderboardId id) {
        this.collection = collection;
        this.id = id;
        scope = and(eq("provider", id.provider()), eq("leaderboard", id.leaderboard()));
        live = and(scope, eq("removed", false));
    }

    @Override
    public long putVersioned(String key, S score) {
        return execute(() -> {
            for (int attempt = 1; ; attempt++) {
                Document update = new Document("score", new Document("$literal", score)).append("removed", false);
                try {
                    return collection.findOneAndUpdate(entry(key), nextVersion(update), UPSERT_RETURN_VERSION).getLong("version");
                } catch (MongoCommandException e) {
                    if (e.getErrorCode() != DUPLICATE_KEY || attempt == PUT_ATTEMPTS) {
                        throw e;
                    }
                }
            }
        });
    }

    @Override
    public OptionalLong removeVersioned(String key) {
        return execute(() -> {
            Document removed = collection.findOneAndUpdate(and(live, eq("key", key)),
                    nextVersion(new Document("removed", true)), RETURN_VERSION);
            return removed == null ? OptionalLong.empty() : OptionalLong.of(removed.getLong("version"));
        });
    }

    @Override
    public Optional<S> score(String key) {
        return execute(() -> Optional.ofNullable(liveEntry(key)).map(this::score));
    }

    @Override
    public OptionalLong rank(String key) {
        return execute(() -> {
            Document entry = liveEntry(key);
            if (entry == null) {
                return OptionalLong.empty();
            }
            long ahead = collection.countDocuments(and(live, between(null, new StoredEntry<>(key, score(entry)))));
            return OptionalLong.of(ahead + 1);
        });
    }

    @Override
    public List<StoredEntry<S>> top(int n) {
        if (n == 0) {
            return List.of();
        }
        return execute(() -> collection.find(live).sort(DESCENDING).limit(n).projection(ENTRY_FIELDS)
                .map(document -> new StoredEntry<>(document.getString("key"), score(document)))
                .into(new ArrayList<>()));
    }

    @Override
    public Optional<StoredEntry<S>> entryBehind(StoredEntry<S> position, int offset) {
        Bson filter = position == null ? live : and(live, or(lt("score", position.score()),
                and(eq("score", position.score()), lt("key", position.key()))));
        return execute(() -> Optional.ofNullable(collection.find(filter).sort(DESCENDING).skip(offset - 1).limit(1).projection(ENTRY_FIELDS)
                .map(document -> new StoredEntry<>(document.getString("key"), score(document)))
                .first()));
    }

    @Override
    public long countBetween(StoredEntry<S> ahead, StoredEntry<S> behind) {
        return execute(() -> collection.countDocuments(and(live, between(ahead, behind))));
    }

    @Override
    public long size() {
        return execute(() -> collection.countDocuments(live));
    }

    @Override
    public ScanPage<S> scan(String cursor, int count) {
        return page(after(live, cursor), count);
    }

    @Override
    public ScanPage<S> scanChanges(long sinceVersion, String cursor, int count) {
        return page(after(and(scope, gte("version", sinceVersion)), cursor), count);
    }

    @Override
    public void purgeRemoved(long beforeVersion) {
        execute(() -> collection.deleteMany(and(scope, eq("removed", true), lt("version", beforeVersion))));
    }

    private ScanPage<S> page(Bson filter, int count) {
        List<VersionedEntry<S>> entries = execute(() -> collection.find(filter).sort(BY_KEY).limit(count).projection(ENTRY_FIELDS)
                .map(document -> document.getBoolean("removed")
                        ? VersionedEntry.<S>removed(document.getString("key"), document.getLong("version"))
                        : new VersionedEntry<>(document.getString("key"), score(document), document.getLong("version")))
                .into(new ArrayList<>()));
        return ScanPage.of(entries, count);
    }

    private static Bson between(StoredEntry<?> ahead, StoredEntry<?> behind) {
        Object low = behind.score();
        Bson lowEnd = and(eq("score", low), gt("key", behind.key()));
        if (ahead == null) {
            return or(gt("score", low), lowEnd);
        }
        Object high = ahead.score();
        if (high.equals(low)) {
            return and(eq("score", low), gt("key", behind.key()), lt("key", ahead.key()));
        }
        return or(and(eq("score", high), lt("key", ahead.key())), and(gt("score", low), lt("score", high)), lowEnd);
    }

    private Document liveEntry(String key) {
        return collection.find(and(live, eq("key", key))).projection(Projections.include("score")).first();
    }

    private Bson entry(String key) {
        return and(scope, eq("key", key));
    }

    private static Bson after(Bson filter, String cursor) {
        return cursor == null ? filter : and(filter, gt("key", cursor));
    }

    private static List<Bson> nextVersion(Document fields) {
        long now = VersionedStorage.now();
        Document version = new Document("$max", List.of(
                new Document("$add", List.of(new Document("$ifNull", List.of("$version", 0L)), 1L)), now));
        return List.of(new Document("$set", fields.append("version", version)));
    }

    @SuppressWarnings("unchecked")
    private S score(Document document) {
        return (S) document.get("score");
    }

    private <T> T execute(Supplier<T> work) {
        try {
            return work.get();
        } catch (MongoException e) {
            throw new StorageException("Storage failure on leaderboard " + id, e);
        }
    }
}
