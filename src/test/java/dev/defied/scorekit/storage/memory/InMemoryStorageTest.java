package dev.defied.scorekit.storage.memory;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.StoredEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryStorageTest {

    private final InMemoryStorage<Double> storage = new InMemoryStorage<>(ScoreType.NUMERIC);

    @Test
    void topReturnsHighestScoresFirst() {
        storage.put("a", 10.0);
        storage.put("b", 30.0);
        storage.put("c", 20.0);

        assertEquals(List.of(new StoredEntry<>("b", 30.0), new StoredEntry<>("c", 20.0)), storage.top(2));
    }

    @Test
    void tiesAreOrderedByKeyDescending() {
        storage.put("a", 5.0);
        storage.put("c", 5.0);
        storage.put("b", 5.0);

        assertEquals(List.of("c", "b", "a"), storage.top(3).stream().map(StoredEntry::key).toList());
        assertEquals(OptionalLong.of(3), storage.rank("a"));
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
        InMemoryStorage<String> lex = new InMemoryStorage<>(ScoreType.LEXICOGRAPHIC);
        lex.put("x", "apple");
        lex.put("y", "banana");
        lex.put("z", "😀");

        assertEquals(List.of("z", "y", "x"), lex.top(3).stream().map(StoredEntry::key).toList());
    }

    @Test
    void concurrentUpdatesKeepRankingConsistent() throws InterruptedException {
        int keys = 100;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        for (int thread = 0; thread < 8; thread++) {
            executor.execute(() -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i = 0; i < 10_000; i++) {
                    String key = "k" + random.nextInt(keys);
                    if (random.nextInt(10) == 0) {
                        storage.remove(key);
                    } else {
                        storage.put(key, (double) random.nextInt(50));
                    }
                }
            });
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        List<StoredEntry<Double>> all = storage.top(Integer.MAX_VALUE);
        assertEquals(storage.size(), all.size());
        for (int i = 0; i < all.size(); i++) {
            StoredEntry<Double> entry = all.get(i);
            assertEquals(OptionalLong.of(i + 1), storage.rank(entry.key()));
            assertEquals(Optional.of(entry.score()), storage.score(entry.key()));
        }
    }
}
