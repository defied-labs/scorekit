package dev.defied.scorekit.storage;

public record StorageSettings(boolean cached, RankSampling rankSampling) {

    public static final StorageSettings DEFAULT = new StorageSettings(true, null);
}
