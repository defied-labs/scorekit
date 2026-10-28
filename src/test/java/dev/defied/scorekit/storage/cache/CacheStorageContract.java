package dev.defied.scorekit.storage.cache;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.StoredEntry;
import dev.defied.scorekit.storage.VersionedEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public abstract class CacheStorageContract {

    protected abstract <S> CacheStorage<S> createCache(ScoreType<S> type);

    @Test
    void coldCacheRejectsReads() {
        CacheStorage<Double> cache = createCache(ScoreType.NUMERIC);
        cache.putIfNewer("a", 1.0, 1);

        assertThrows(CacheColdException.class, () -> cache.score("a"));
        assertThrows(CacheColdException.class, () -> cache.rank("a"));
        assertThrows(CacheColdException.class, () -> cache.top(1));
        assertThrows(CacheColdException.class, cache::size);
    }

    @Test
    void coldCacheIgnoresWrites() {
        CacheStorage<Double> cache = createCache(ScoreType.NUMERIC);
        cache.putIfNewer("a", 1.0, 1);
        cache.removeIfNewer("b", 1);

        assertEquals(0, warm(cache).size());
        cache.putIfNewer("b", 2.0, 1);
        assertEquals(Optional.of(2.0), cache.score("b"));
    }

    @Test
    void writesDuringWarmUpAreKept() {
        CacheStorage<Double> cache = createCache(ScoreType.NUMERIC);
        long epoch = cache.state().beginWarmUp().orElseThrow();

        cache.putIfNewer("a", 1.0, 1);
        cache.state().completeWarmUp(epoch, 0);

        assertEquals(Optional.of(1.0), cache.score("a"));
    }

    @Test
    void warmCacheServesReads() {
        CacheStorage<Double> cache = warm(createCache(ScoreType.NUMERIC));
        cache.putIfNewer("a", 1.0, 1);
        cache.putIfNewer("b", Double.POSITIVE_INFINITY, 1);
        cache.putIfNewer("c", 0.1 + 0.2, 1);

        assertEquals(Optional.of(0.1 + 0.2), cache.score("c"));
        assertEquals(OptionalLong.of(3), cache.rank("c"));
        assertEquals(List.of(new StoredEntry<>("b", Double.POSITIVE_INFINITY), new StoredEntry<>("a", 1.0)), cache.top(2));
        assertEquals(3, cache.size());
    }

    @Test
    void olderWritesAreIgnored() {
        CacheStorage<Double> cache = warm(createCache(ScoreType.NUMERIC));

        cache.putIfNewer("a", 2.0, 10);
        cache.putIfNewer("a", 1.0, 5);
        cache.putIfNewer("a", 3.0, 10);

        assertEquals(Optional.of(2.0), cache.score("a"));
    }

    @Test
    void removalBlocksOlderWrites() {
        CacheStorage<Double> cache = warm(createCache(ScoreType.NUMERIC));
        cache.putIfNewer("a", 1.0, 10);

        cache.removeIfNewer("a", 11);
        cache.putIfNewer("a", 2.0, 10);
        cache.removeIfNewer("unknown", 5);
        cache.putIfNewer("unknown", 2.0, 4);

        assertEquals(Optional.empty(), cache.score("a"));
        assertEquals(Optional.empty(), cache.score("unknown"));
        assertEquals(0, cache.size());

        cache.putIfNewer("a", 3.0, 12);
        assertEquals(Optional.of(3.0), cache.score("a"));
    }

    @Test
    void olderRemovalIsIgnored() {
        CacheStorage<Double> cache = warm(createCache(ScoreType.NUMERIC));
        cache.putIfNewer("a", 1.0, 10);

        cache.removeIfNewer("a", 9);

        assertEquals(Optional.of(1.0), cache.score("a"));
    }

    @Test
    void applyAllAppliesPutsAndRemovalsInOrder() {
        CacheStorage<Double> cache = warm(createCache(ScoreType.NUMERIC));

        cache.applyAll(List.of(
                new VersionedEntry<>("a", 1.0, 1),
                new VersionedEntry<>("b", 2.0, 1),
                VersionedEntry.removed("a", 2),
                new VersionedEntry<>("a", 5.0, 1)));

        assertEquals(List.of(new StoredEntry<>("b", 2.0)), cache.top(10));
    }

    @Test
    void clearEmptiesCacheButKeepsWarmState() {
        CacheStorage<Double> cache = warm(createCache(ScoreType.NUMERIC));
        cache.putIfNewer("a", 1.0, 10);

        cache.clear();
        cache.putIfNewer("a", 2.0, 5);

        assertEquals(Optional.of(2.0), cache.score("a"));
    }

    @Test
    void lexicographicCacheOrdersAndVersionsEntries() {
        CacheStorage<String> cache = warm(createCache(ScoreType.LEXICOGRAPHIC));
        cache.putIfNewer("x", "apple", 1);
        cache.putIfNewer("y", "banana", 1);
        cache.putIfNewer("x", "cherry", 2);
        cache.putIfNewer("x", "aardvark", 1);
        cache.putIfNewer("z", "zebra", 1);
        cache.removeIfNewer("z", 2);

        assertEquals(List.of(new StoredEntry<>("x", "cherry"), new StoredEntry<>("y", "banana")), cache.top(10));
        assertEquals(OptionalLong.of(2), cache.rank("y"));
        assertEquals(Optional.of("cherry"), cache.score("x"));
        assertEquals(2, cache.size());
    }

    private static <S> CacheStorage<S> warm(CacheStorage<S> cache) {
        long epoch = cache.state().beginWarmUp().orElseThrow();
        cache.state().completeWarmUp(epoch, 0);
        return cache;
    }
}
