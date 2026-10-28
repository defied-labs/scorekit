package dev.defied.scorekit.storage;

import dev.defied.scorekit.codec.ScoreType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class VersionedStorageContract extends LeaderboardStorageContract {

    @Override
    protected abstract <S> VersionedStorage<S> create(ScoreType<S> type);

    @Test
    void versionsIncreaseOnEveryWrite() {
        VersionedStorage<Double> storage = create(ScoreType.NUMERIC);

        long first = storage.putVersioned("a", 1.0);
        long second = storage.putVersioned("a", 2.0);
        long removed = storage.removeVersioned("a").orElseThrow();
        long revived = storage.putVersioned("a", 3.0);

        assertTrue(first < second && second < removed && removed < revived);
    }

    @Test
    void removingAbsentKeyHasNoVersion() {
        VersionedStorage<Double> storage = create(ScoreType.NUMERIC);
        storage.put("a", 1.0);
        storage.remove("a");

        assertEquals(OptionalLong.empty(), storage.removeVersioned("a"));
        assertEquals(OptionalLong.empty(), storage.removeVersioned("never"));
    }

    @Test
    void scanReturnsLiveEntriesWithVersions() {
        VersionedStorage<Double> storage = create(ScoreType.NUMERIC);
        long version = 0;
        for (int i = 0; i < 10; i++) {
            version = storage.putVersioned("k" + i, (double) i);
        }
        storage.remove("k3");

        Map<String, VersionedEntry<Double>> scanned = scanAll(cursor -> storage.scan(cursor, 3));

        assertEquals(9, scanned.size());
        assertEquals(new VersionedEntry<>("k9", 9.0, version), scanned.get("k9"));
        assertNull(scanned.get("k3"));
    }

    @Test
    void scanOfEmptyStorageFinishes() {
        ScanPage<Double> page = create(ScoreType.NUMERIC).scan(null, 10);

        assertEquals(List.of(), page.entries());
        assertNull(page.next());
    }

    @Test
    void lexicographicScanReturnsLiveEntries() {
        VersionedStorage<String> lex = create(ScoreType.LEXICOGRAPHIC);
        lex.put("a", "x");
        lex.put("b", "y");

        Map<String, VersionedEntry<String>> scanned = scanAll(cursor -> lex.scan(cursor, 1));

        assertEquals("x", scanned.get("a").score());
        assertEquals("y", scanned.get("b").score());
    }

    @Test
    void scanChangesIncludesRemovalsSinceVersion() throws InterruptedException {
        VersionedStorage<Double> storage = create(ScoreType.NUMERIC);
        storage.put("a", 1.0);
        long since = storage.putVersioned("b", 2.0) + 1;
        Thread.sleep(2);
        long added = storage.putVersioned("c", 3.0);
        long removed = storage.removeVersioned("a").orElseThrow();

        Map<String, VersionedEntry<Double>> changes = scanAll(cursor -> storage.scanChanges(since, cursor, 1));

        assertEquals(Map.of("a", VersionedEntry.removed("a", removed), "c", new VersionedEntry<>("c", 3.0, added)), changes);
    }

    @Test
    void purgeRemovedDropsOldRemovals() {
        VersionedStorage<Double> storage = create(ScoreType.NUMERIC);
        storage.put("a", 1.0);
        long removed = storage.removeVersioned("a").orElseThrow();

        storage.purgeRemoved(removed + 1);

        assertEquals(List.of(), storage.scanChanges(0, null, 10).entries());
    }

    @Test
    void entryBehindWalksTheRanking() {
        VersionedStorage<Double> storage = ranked();

        assertEquals(Optional.of(new StoredEntry<>("a", 3.0)), storage.entryBehind(null, 1));
        assertEquals(Optional.of(new StoredEntry<>("b", 2.0)), storage.entryBehind(null, 3));
        assertEquals(Optional.of(new StoredEntry<>("b", 2.0)), storage.entryBehind(new StoredEntry<>("c", 2.0), 1));
        assertEquals(Optional.of(new StoredEntry<>("e", 1.0)), storage.entryBehind(new StoredEntry<>("a", 3.0), 3));
        assertEquals(Optional.of(new StoredEntry<>("e", 1.0)), storage.entryBehind(new StoredEntry<>("x", 1.5), 1));
        assertEquals(Optional.empty(), storage.entryBehind(new StoredEntry<>("b", 2.0), 2));
    }

    @Test
    void countBetweenCountsLiveEntriesStrictlyInside() {
        VersionedStorage<Double> storage = ranked();

        assertEquals(0, storage.countBetween(null, new StoredEntry<>("a", 3.0)));
        assertEquals(3, storage.countBetween(null, new StoredEntry<>("e", 1.0)));
        assertEquals(2, storage.countBetween(new StoredEntry<>("a", 3.0), new StoredEntry<>("e", 1.0)));
        assertEquals(0, storage.countBetween(new StoredEntry<>("c", 2.0), new StoredEntry<>("b", 2.0)));
        assertEquals(2, storage.countBetween(new StoredEntry<>("z", 2.0), new StoredEntry<>("a", 2.0)));
        assertEquals(1, storage.countBetween(new StoredEntry<>("c", 2.0), new StoredEntry<>("x", 1.5)));
    }

    @Test
    void lexicographicWindowsFollowTheRanking() {
        VersionedStorage<String> lex = create(ScoreType.LEXICOGRAPHIC);
        lex.put("x", "apple");
        lex.put("y", "banana");
        lex.put("z", "cherry");

        assertEquals(Optional.of(new StoredEntry<>("y", "banana")), lex.entryBehind(new StoredEntry<>("z", "cherry"), 1));
        assertEquals(1, lex.countBetween(new StoredEntry<>("z", "cherry"), new StoredEntry<>("x", "apple")));
        assertEquals(2, lex.countBetween(null, new StoredEntry<>("x", "apple")));
    }

    @Test
    void sampledRanksMatchExactRanksWithoutChanges() {
        VersionedStorage<Double> storage = create(ScoreType.NUMERIC);
        for (int i = 0; i < 200; i++) {
            storage.put("k" + i, (double) (i % 13));
        }
        SampledRankStorage<Double> sampled = new SampledRankStorage<>(storage, ScoreType.NUMERIC, new RankSampling(7, Duration.ofHours(1)));
        sampled.resample();

        for (int i = 0; i < 200; i++) {
            assertEquals(storage.rank("k" + i), sampled.rank("k" + i));
        }
    }

    @Test
    void sampledLexicographicRanksMatchExactRanks() {
        VersionedStorage<String> lex = create(ScoreType.LEXICOGRAPHIC);
        for (int i = 0; i < 40; i++) {
            lex.put("k" + i, "s" + (i % 6));
        }
        SampledRankStorage<String> sampled = new SampledRankStorage<>(lex, ScoreType.LEXICOGRAPHIC, new RankSampling(4, Duration.ofHours(1)));
        sampled.resample();

        for (int i = 0; i < 40; i++) {
            assertEquals(lex.rank("k" + i), sampled.rank("k" + i));
        }
    }

    private VersionedStorage<Double> ranked() {
        VersionedStorage<Double> storage = create(ScoreType.NUMERIC);
        storage.put("a", 3.0);
        storage.put("b", 2.0);
        storage.put("c", 2.0);
        storage.put("d", 2.5);
        storage.remove("d");
        storage.put("e", 1.0);
        return storage;
    }

    private static <S> Map<String, VersionedEntry<S>> scanAll(Scanner<S> scanner) {
        Map<String, VersionedEntry<S>> scanned = new HashMap<>();
        String cursor = null;
        do {
            ScanPage<S> page = scanner.scan(cursor);
            page.entries().forEach(entry -> scanned.put(entry.key(), entry));
            cursor = page.next();
        } while (cursor != null);
        return scanned;
    }

    private interface Scanner<S> {
        ScanPage<S> scan(String cursor);
    }
}
