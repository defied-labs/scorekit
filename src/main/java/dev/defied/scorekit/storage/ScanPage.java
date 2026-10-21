package dev.defied.scorekit.storage;

import java.util.List;

public record ScanPage<S>(List<VersionedEntry<S>> entries, String next) {

    public static <S> ScanPage<S> of(List<VersionedEntry<S>> entries, int count) {
        return new ScanPage<>(entries, entries.isEmpty() || entries.size() < count ? null : entries.getLast().key());
    }
}
