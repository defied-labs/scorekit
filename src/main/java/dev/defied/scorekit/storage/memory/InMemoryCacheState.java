package dev.defied.scorekit.storage.memory;

import dev.defied.scorekit.storage.cache.CacheState;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

final class InMemoryCacheState implements CacheState {

    private final Set<String> dirty = new LinkedHashSet<>();
    private boolean warm;
    private long epoch;
    private long warmingEpoch;

    @Override
    public synchronized boolean isWarm() {
        return warm;
    }

    @Override
    public synchronized boolean markDirtyUnlessWarm(String key) {
        if (warm) {
            return false;
        }
        dirty.add(key);
        return true;
    }

    @Override
    public synchronized OptionalLong beginWarmUp() {
        if (warm || warmingEpoch != 0) {
            return OptionalLong.empty();
        }
        warmingEpoch = ++epoch;
        dirty.clear();
        return OptionalLong.of(epoch);
    }

    @Override
    public synchronized boolean renewWarmUp(long epoch) {
        return epoch == this.epoch && epoch == warmingEpoch;
    }

    @Override
    public synchronized List<String> pollDirty(int count) {
        List<String> polled = dirty.stream().limit(count).toList();
        polled.forEach(dirty::remove);
        return polled;
    }

    @Override
    public synchronized boolean completeWarmUp(long epoch) {
        if (!renewWarmUp(epoch) || !dirty.isEmpty()) {
            return false;
        }
        warm = true;
        warmingEpoch = 0;
        return true;
    }

    @Override
    public synchronized void abortWarmUp(long epoch) {
        if (warmingEpoch == epoch) {
            warmingEpoch = 0;
        }
    }

    @Override
    public synchronized void invalidate() {
        warm = false;
        epoch++;
    }
}
