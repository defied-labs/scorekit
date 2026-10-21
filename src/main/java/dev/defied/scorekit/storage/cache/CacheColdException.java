package dev.defied.scorekit.storage.cache;

public class CacheColdException extends RuntimeException {

    public CacheColdException() {
        super("Cache is not warm");
    }
}
