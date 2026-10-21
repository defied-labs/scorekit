package dev.defied.scorekit.storage.cache;

import java.util.List;
import java.util.OptionalLong;

public interface CacheState {

    boolean isWarm();

    boolean markDirtyUnlessWarm(String key);

    OptionalLong beginWarmUp();

    boolean renewWarmUp(long epoch);

    List<String> pollDirty(int count);

    boolean completeWarmUp(long epoch);

    void abortWarmUp(long epoch);

    void invalidate();
}
