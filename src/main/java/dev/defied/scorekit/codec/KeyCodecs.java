package dev.defied.scorekit.codec;

import java.util.UUID;

public final class KeyCodecs {

    public static final KeyCodec<String> STRING = KeyCodec.of(key -> key, key -> key);
    public static final KeyCodec<UUID> UUID = KeyCodec.of(java.util.UUID::toString, java.util.UUID::fromString);
    public static final KeyCodec<Integer> INTEGER = KeyCodec.of(String::valueOf, Integer::valueOf);
    public static final KeyCodec<Long> LONG = KeyCodec.of(String::valueOf, Long::valueOf);

    private KeyCodecs() {
    }
}
