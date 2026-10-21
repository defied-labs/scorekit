package dev.defied.scorekit.storage;

import dev.defied.scorekit.codec.ScoreType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
