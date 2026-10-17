package dev.defied.scorekit.codec;

import java.util.function.Function;

public interface ScoreCodec<V, S> {

    ScoreType<S> type();

    S encode(V value);

    V decode(S encoded);

    static <V, S> ScoreCodec<V, S> of(ScoreType<S> type, Function<V, S> encoder, Function<S, V> decoder) {
        return new ScoreCodec<>() {
            @Override
            public ScoreType<S> type() {
                return type;
            }

            @Override
            public S encode(V value) {
                return encoder.apply(value);
            }

            @Override
            public V decode(S encoded) {
                return decoder.apply(encoded);
            }
        };
    }
}
