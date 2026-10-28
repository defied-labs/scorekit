package dev.defied.scorekit.storage;

public record StorageSettings(boolean cached) {

    public static final StorageSettings DEFAULT = new StorageSettings(true);
}
