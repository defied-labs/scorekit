package dev.defied.scorekit.storage.memory;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.ScanPage;
import dev.defied.scorekit.storage.StoredEntry;

import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class InMemoryStorage<S> implements LeaderboardStorage<S> {

    private final NavigableMap<String, S> scores = new TreeMap<>(ScoreType.LEXICOGRAPHIC.order());
    private final NavigableSet<StoredEntry<S>> ranking;
    private final Lock readLock;
    private final Lock writeLock;

    public InMemoryStorage(ScoreType<S> type) {
        Comparator<StoredEntry<S>> ascending = Comparator.comparing(StoredEntry<S>::score, type.order())
                .thenComparing(StoredEntry::key, ScoreType.LEXICOGRAPHIC.order());
        ranking = new TreeSet<>(ascending.reversed());
        ReadWriteLock lock = new ReentrantReadWriteLock();
        readLock = lock.readLock();
        writeLock = lock.writeLock();
    }

    @Override
    public void put(String key, S score) {
        writeLock.lock();
        try {
            S previous = scores.put(key, score);
            if (previous != null) {
                ranking.remove(new StoredEntry<>(key, previous));
            }
            ranking.add(new StoredEntry<>(key, score));
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
    public boolean remove(String key) {
        writeLock.lock();
        try {
            S previous = scores.remove(key);
            if (previous == null) {
                return false;
            }
            ranking.remove(new StoredEntry<>(key, previous));
            return true;
        } finally {
            writeLock.unlock();
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
            List<StoredEntry<S>> entries = remaining.entrySet().stream()
                    .limit(count)
                    .map(entry -> new StoredEntry<>(entry.getKey(), entry.getValue()))
                    .toList();
            return new ScanPage<>(entries, entries.isEmpty() || entries.size() < count ? null : entries.getLast().key());
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void clear() {
        writeLock.lock();
        try {
            scores.clear();
            ranking.clear();
        } finally {
            writeLock.unlock();
        }
    }
}
