package dev.defied.scorekit.storage;

import dev.defied.scorekit.codec.ScoreType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class LeaderboardStorageContract {

    private LeaderboardStorage<Double> storage;

    protected abstract <S> LeaderboardStorage<S> create(ScoreType<S> type);

    @BeforeEach
    void createStorage() {
        storage = create(ScoreType.NUMERIC);
    }

    @Test
    void topReturnsHighestScoresFirst() {
        storage.put("a", 10.0);
        storage.put("b", 30.0);
        storage.put("c", 20.0);

        assertEquals(List.of(new StoredEntry<>("b", 30.0), new StoredEntry<>("c", 20.0)), storage.top(2));
    }

    @Test
    void topOfZeroIsEmpty() {
        storage.put("a", 1.0);

        assertEquals(List.of(), storage.top(0));
    }

    @Test
    void topBeyondSizeReturnsAll() {
        storage.put("a", 1.0);

        assertEquals(List.of(new StoredEntry<>("a", 1.0)), storage.top(Integer.MAX_VALUE));
    }

    @Test
    void tiesAreOrderedByKeyDescending() {
        storage.put("a", 5.0);
        storage.put("c", 5.0);
        storage.put("b", 5.0);

        assertEquals(List.of("c", "b", "a"), keys(storage.top(3)));
        assertEquals(OptionalLong.of(3), storage.rank("a"));
    }

    @Test
    void infiniteScoresAreOrdered() {
        storage.put("low", Double.NEGATIVE_INFINITY);
        storage.put("mid", 0.0);
        storage.put("high", Double.POSITIVE_INFINITY);

        assertEquals(List.of(new StoredEntry<>("high", Double.POSITIVE_INFINITY), new StoredEntry<>("mid", 0.0),
                new StoredEntry<>("low", Double.NEGATIVE_INFINITY)), storage.top(3));
    }

    @Test
    void scoresKeepFullPrecision() {
        double score = 0.1 + 0.2;
        storage.put("a", score);

        assertEquals(Optional.of(score), storage.score("a"));
        assertEquals(score, storage.top(1).getFirst().score());
    }

    @Test
    void putReplacesPreviousScore() {
        storage.put("a", 1.0);
        storage.put("b", 2.0);
        storage.put("a", 3.0);

        assertEquals(2, storage.size());
        assertEquals(Optional.of(3.0), storage.score("a"));
        assertEquals(OptionalLong.of(1), storage.rank("a"));
        assertEquals(OptionalLong.of(2), storage.rank("b"));
    }

    @Test
    void removeDropsKey() {
        storage.put("a", 1.0);

        assertTrue(storage.remove("a"));
        assertFalse(storage.remove("a"));
        assertEquals(0, storage.size());
        assertEquals(Optional.empty(), storage.score("a"));
        assertEquals(OptionalLong.empty(), storage.rank("a"));
        assertEquals(List.of(), storage.top(10));
    }

    @Test
    void lexicographicScoresOrderByCodePoint() {
        LeaderboardStorage<String> lex = create(ScoreType.LEXICOGRAPHIC);
        lex.put("x", "apple");
        lex.put("y", "banana");
        lex.put("z", "😀");
        lex.put("w", "￿");

        assertEquals(List.of("z", "w", "y", "x"), keys(lex.top(4)));
    }

    @Test
    void lexicographicPrefixRanksBelowLongerScore() {
        LeaderboardStorage<String> lex = create(ScoreType.LEXICOGRAPHIC);
        lex.put("a", "ab");
        lex.put("b", "a");
        lex.put("c", "a");

        assertEquals(List.of(new StoredEntry<>("a", "ab"), new StoredEntry<>("c", "a"), new StoredEntry<>("b", "a")), lex.top(3));
        assertEquals(OptionalLong.of(3), lex.rank("b"));
    }

    @Test
    void lexicographicPutReplacesAndRemoveDrops() {
        LeaderboardStorage<String> lex = create(ScoreType.LEXICOGRAPHIC);
        lex.put("a", "m");
        lex.put("b", "n");
        lex.put("a", "z");

        assertEquals(2, lex.size());
        assertEquals(Optional.of("z"), lex.score("a"));
        assertEquals(OptionalLong.of(1), lex.rank("a"));

        assertTrue(lex.remove("a"));
        assertFalse(lex.remove("a"));
        assertEquals(OptionalLong.empty(), lex.rank("a"));
        assertEquals(List.of(new StoredEntry<>("b", "n")), lex.top(10));
    }

    @Test
    void concurrentUpdatesKeepRankingConsistent() throws Exception {
        LeaderboardStorage<String> lex = create(ScoreType.LEXICOGRAPHIC);
        Parallel.run(8, () -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 2_000; i++) {
                String key = "k" + random.nextInt(50);
                if (random.nextInt(10) == 0) {
                    storage.remove(key);
                    lex.remove(key);
                } else {
                    storage.put(key, (double) random.nextInt(20));
                    lex.put(key, "s" + random.nextInt(20));
                }
            }
        });

        assertConsistent(storage);
        assertConsistent(lex);
    }

    private static <S> void assertConsistent(LeaderboardStorage<S> storage) {
        List<StoredEntry<S>> all = storage.top(Integer.MAX_VALUE);
        assertEquals(storage.size(), all.size());
        for (int i = 0; i < all.size(); i++) {
            StoredEntry<S> entry = all.get(i);
            assertEquals(OptionalLong.of(i + 1), storage.rank(entry.key()));
            assertEquals(Optional.of(entry.score()), storage.score(entry.key()));
        }
    }

    private static <S> List<String> keys(List<StoredEntry<S>> entries) {
        return entries.stream().map(StoredEntry::key).toList();
    }
}
