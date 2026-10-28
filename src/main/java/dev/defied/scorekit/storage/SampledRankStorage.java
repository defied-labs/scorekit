package dev.defied.scorekit.storage;

import dev.defied.scorekit.codec.ScoreType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.System.Logger.Level.WARNING;

final class SampledRankStorage<S> implements VersionedStorage<S> {

    private static final int PUBLISH_EVERY = 1_000;
    private static final System.Logger LOGGER = System.getLogger(SampledRankStorage.class.getName());

    private final VersionedStorage<S> storage;
    private final Comparator<StoredEntry<S>> ranking;
    private final RankSampling sampling;
    private final AtomicBoolean resampling = new AtomicBoolean();
    private volatile List<StoredEntry<S>> samples = List.of();
    private volatile long nextSampleAt;

    SampledRankStorage(VersionedStorage<S> storage, ScoreType<S> type, RankSampling sampling) {
        this.storage = storage;
        this.ranking = StoredEntry.ranking(type);
        this.sampling = sampling;
    }

    @Override
    public OptionalLong rank(String key) {
        Optional<S> score = storage.score(key);
        if (score.isEmpty()) {
            return OptionalLong.empty();
        }
        resampleIfStale();
        StoredEntry<S> position = new StoredEntry<>(key, score.get());
        List<StoredEntry<S>> current = samples;
        int found = Collections.binarySearch(current, position, ranking);
        int ahead = found >= 0 ? found : -found - 1;
        StoredEntry<S> base = ahead == 0 ? null : current.get(ahead - 1);
        return OptionalLong.of((long) ahead * sampling.interval() + storage.countBetween(base, position) + 1);
    }

    void resample() {
        nextSampleAt = System.currentTimeMillis() + sampling.refresh().toMillis();
        boolean first = samples.isEmpty();
        List<StoredEntry<S>> fresh = new ArrayList<>();
        Optional<StoredEntry<S>> next = storage.entryBehind(null, sampling.interval());
        while (next.isPresent()) {
            fresh.add(next.get());
            if (first && fresh.size() % PUBLISH_EVERY == 0) {
                samples = List.copyOf(fresh);
            }
            next = storage.entryBehind(next.get(), sampling.interval());
        }
        samples = List.copyOf(fresh);
    }

    private void resampleIfStale() {
        if (System.currentTimeMillis() < nextSampleAt || !resampling.compareAndSet(false, true)) {
            return;
        }
        Thread.ofVirtual().name("scorekit-rank-sampling").start(() -> {
            try {
                resample();
            } catch (RuntimeException e) {
                LOGGER.log(WARNING, "Rank sampling failed", e);
            } finally {
                resampling.set(false);
            }
        });
    }

    @Override
    public long putVersioned(String key, S score) {
        return storage.putVersioned(key, score);
    }

    @Override
    public OptionalLong removeVersioned(String key) {
        return storage.removeVersioned(key);
    }

    @Override
    public ScanPage<S> scan(String cursor, int count) {
        return storage.scan(cursor, count);
    }

    @Override
    public ScanPage<S> scanChanges(long sinceVersion, String cursor, int count) {
        return storage.scanChanges(sinceVersion, cursor, count);
    }

    @Override
    public void purgeRemoved(long beforeVersion) {
        storage.purgeRemoved(beforeVersion);
    }

    @Override
    public Optional<StoredEntry<S>> entryBehind(StoredEntry<S> position, int offset) {
        return storage.entryBehind(position, offset);
    }

    @Override
    public long countBetween(StoredEntry<S> ahead, StoredEntry<S> behind) {
        return storage.countBetween(ahead, behind);
    }

    @Override
    public Optional<S> score(String key) {
        return storage.score(key);
    }

    @Override
    public List<StoredEntry<S>> top(int n) {
        return storage.top(n);
    }

    @Override
    public long size() {
        return storage.size();
    }
}
