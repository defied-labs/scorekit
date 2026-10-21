package dev.defied.scorekit.storage.cache;

import dev.defied.scorekit.storage.StoredEntry;
import dev.defied.scorekit.storage.VersionedEntry;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

public interface CacheStorage<S> {

    Optional<S> score(String key);

    OptionalLong rank(String key);

    List<StoredEntry<S>> top(int n);

    long size();

    void putIfNewer(String key, S score, long version);

    void removeIfNewer(String key, long version);

    void applyAll(List<VersionedEntry<S>> entries);

    void clear();

    CacheState state();
}
