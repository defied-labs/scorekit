package dev.defied.scorekit.storage.memory;

import dev.defied.scorekit.storage.cache.CacheState;

import java.util.OptionalLong;

final class InMemoryCacheState implements CacheState {

    private boolean warm;
    private long epoch;
    private long warmingEpoch;
    private long watermark;
    private long nextSweepAt;

    @Override
    public synchronized boolean isWarm() {
        return warm;
    }

    synchronized boolean isAccepting() {
        return warm || warmingEpoch != 0;
    }

    @Override
    public synchronized OptionalLong beginWarmUp() {
        if (warm || warmingEpoch != 0) {
            return OptionalLong.empty();
        }
        warmingEpoch = ++epoch;
        return OptionalLong.of(warmingEpoch);
    }

    @Override
    public synchronized boolean renewWarmUp(long epoch) {
        return epoch == warmingEpoch;
    }

    @Override
    public synchronized boolean completeWarmUp(long epoch, long watermark) {
        if (epoch != warmingEpoch) {
            return false;
        }
        warm = true;
        warmingEpoch = 0;
        this.watermark = watermark;
        return true;
    }

    @Override
    public synchronized void abortWarmUp(long epoch) {
        if (epoch == warmingEpoch) {
            warmingEpoch = 0;
        }
    }

    @Override
    public synchronized OptionalLong tryStartSweep(long intervalMillis) {
        long now = System.currentTimeMillis();
        if (!warm || now < nextSweepAt) {
            return OptionalLong.empty();
        }
        nextSweepAt = now + intervalMillis;
        return OptionalLong.of(watermark);
    }

    @Override
    public synchronized void completeSweep(long watermark) {
        if (warm) {
            this.watermark = watermark;
        }
    }
}
