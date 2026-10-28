package dev.defied.scorekit.storage.cache;

import java.util.OptionalLong;

public interface CacheState {

    boolean isWarm();

    OptionalLong beginWarmUp();

    boolean renewWarmUp(long epoch);

    boolean completeWarmUp(long epoch, long watermark);

    void abortWarmUp(long epoch);

    OptionalLong tryStartSweep(long intervalMillis);

    void completeSweep(long watermark);

    void touch();
}
