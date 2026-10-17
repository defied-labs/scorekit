package dev.defied.scorekit.codec;

import java.util.function.Function;

public interface KeyCodec<K> {

    String encode(K key);

    K decode(String encoded);

    static <K> KeyCodec<K> of(Function<K, String> encoder, Function<String, K> decoder) {
        return new KeyCodec<>() {
            @Override
            public String encode(K key) {
                return encoder.apply(key);
            }

            @Override
            public K decode(String encoded) {
                return decoder.apply(encoded);
            }
        };
    }
}
