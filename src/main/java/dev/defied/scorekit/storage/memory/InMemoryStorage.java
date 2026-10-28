package dev.defied.scorekit.storage.memory;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.ScanPage;
import dev.defied.scorekit.storage.StoredEntry;
import dev.defied.scorekit.storage.VersionedEntry;
import dev.defied.scorekit.storage.VersionedStorage;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class InMemoryStorage<S> implements VersionedStorage<S> {

    private static final Comparator<String> KEY_ORDER = ScoreType.LEXICOGRAPHIC.order();

    private final NavigableMap<String, S> scores = new TreeMap<>(KEY_ORDER);
    private final Map<String, Long> versions = new HashMap<>();
    private final LinkedHashMap<String, Tombstone> removed = new LinkedHashMap<>();
    private final NavigableSet<StoredEntry<S>> ranking;
    private final Lock readLock;
    private final Lock writeLock;

    public InMemoryStorage(ScoreType<S> type) {
        ranking = new TreeSet<>(StoredEntry.ranking(type));
        ReadWriteLock lock = new ReentrantReadWriteLock();
        readLock = lock.readLock();
        writeLock = lock.writeLock();
    }

    @Override
    public long putVersioned(String key, S score) {
        writeLock.lock();
        try {
            long version = VersionedStorage.next(currentVersion(key));
            store(key, score, version);
            return version;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public OptionalLong removeVersioned(String key) {
        writeLock.lock();
        try {
            if (!scores.containsKey(key)) {
                return OptionalLong.empty();
            }
            long version = VersionedStorage.next(currentVersion(key));
            drop(key, version);
            return OptionalLong.of(version);
        } finally {
            writeLock.unlock();
        }
    }

    void putIfNewer(String key, S score, long version) {
        writeLock.lock();
        try {
            if (currentVersion(key) < version) {
                store(key, score, version);
            }
        } finally {
            writeLock.unlock();
        }
    }

    void removeIfNewer(String key, long version) {
        writeLock.lock();
        try {
            if (currentVersion(key) < version) {
                drop(key, version);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Optional<S> score(String key) {
        readLock.lock();
        try {
            return Optional.ofNullable(scores.get(key));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public OptionalLong rank(String key) {
        readLock.lock();
        try {
            S score = scores.get(key);
            if (score == null) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(ranking.headSet(new StoredEntry<>(key, score), false).size() + 1L);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<StoredEntry<S>> top(int n) {
        readLock.lock();
        try {
            return ranking.stream().limit(n).toList();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Optional<StoredEntry<S>> entryBehind(StoredEntry<S> position, int offset) {
        readLock.lock();
        try {
            NavigableSet<StoredEntry<S>> behind = position == null ? ranking : ranking.tailSet(position, false);
            return behind.stream().skip(offset - 1L).findFirst();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public long countBetween(StoredEntry<S> ahead, StoredEntry<S> behind) {
        readLock.lock();
        try {
            return (ahead == null ? ranking.headSet(behind, false) : ranking.subSet(ahead, false, behind, false)).size();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public long size() {
        readLock.lock();
        try {
            return scores.size();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public ScanPage<S> scan(String cursor, int count) {
        readLock.lock();
        try {
            NavigableMap<String, S> remaining = cursor == null ? scores : scores.tailMap(cursor, false);
            return ScanPage.of(remaining.entrySet().stream()
                    .limit(count)
                    .map(entry -> new VersionedEntry<>(entry.getKey(), entry.getValue(), versions.get(entry.getKey())))
                    .toList(), count);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public ScanPage<S> scanChanges(long sinceVersion, String cursor, int count) {
        readLock.lock();
        try {
            Stream<VersionedEntry<S>> live = versions.entrySet().stream()
                    .map(entry -> new VersionedEntry<>(entry.getKey(), scores.get(entry.getKey()), entry.getValue()));
            Stream<VersionedEntry<S>> dropped = removed.entrySet().stream()
                    .map(entry -> VersionedEntry.<S>removed(entry.getKey(), entry.getValue().version()));
            return ScanPage.of(Stream.concat(live, dropped)
                    .filter(entry -> entry.version() >= sinceVersion)
                    .filter(entry -> cursor == null || KEY_ORDER.compare(entry.key(), cursor) > 0)
                    .sorted(Comparator.comparing(VersionedEntry::key, KEY_ORDER))
                    .limit(count)
                    .toList(), count);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void purgeRemoved(long beforeVersion) {
        purgeWhile(tombstone -> tombstone.version() < beforeVersion);
    }

    void expireRemoved(long removedBeforeMillis) {
        purgeWhile(tombstone -> tombstone.removedAtMillis() < removedBeforeMillis);
    }

    private void purgeWhile(Predicate<Tombstone> expired) {
        writeLock.lock();
        try {
            Iterator<Tombstone> oldest = removed.values().iterator();
            while (oldest.hasNext() && expired.test(oldest.next())) {
                oldest.remove();
            }
        } finally {
            writeLock.unlock();
        }
    }

    void clear() {
        writeLock.lock();
        try {
            scores.clear();
            versions.clear();
            removed.clear();
            ranking.clear();
        } finally {
            writeLock.unlock();
        }
    }

    private long currentVersion(String key) {
        Long version = versions.get(key);
        if (version != null) {
            return version;
        }
        Tombstone tombstone = removed.get(key);
        return tombstone == null ? Long.MIN_VALUE : tombstone.version();
    }

    private void store(String key, S score, long version) {
        S previous = scores.put(key, score);
        if (previous != null) {
            ranking.remove(new StoredEntry<>(key, previous));
        }
        ranking.add(new StoredEntry<>(key, score));
        versions.put(key, version);
        removed.remove(key);
    }

    private void drop(String key, long version) {
        S previous = scores.remove(key);
        if (previous != null) {
            ranking.remove(new StoredEntry<>(key, previous));
        }
        versions.remove(key);
        removed.remove(key);
        removed.put(key, new Tombstone(version, System.currentTimeMillis()));
    }

    private record Tombstone(long version, long removedAtMillis) {
    }
}
