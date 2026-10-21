package dev.defied.scorekit.storage;

public record VersionedEntry<S>(String key, S score, long version) {

    public static <S> VersionedEntry<S> removed(String key, long version) {
        return new VersionedEntry<>(key, null, version);
    }

    public boolean isRemoved() {
        return score == null;
    }
}
