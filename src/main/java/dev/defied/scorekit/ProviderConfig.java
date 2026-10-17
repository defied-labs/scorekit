package dev.defied.scorekit;

import dev.defied.scorekit.codec.EvaluationCriteria;
import dev.defied.scorekit.codec.KeyCodec;
import dev.defied.scorekit.codec.KeyCodecs;
import dev.defied.scorekit.codec.ScoreCodec;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ProviderConfig<K, V> {

    private static final Map<Type, KeyCodec<?>> DEFAULT_KEY_CODECS = Map.of(
            String.class, KeyCodecs.STRING,
            UUID.class, KeyCodecs.UUID,
            Integer.class, KeyCodecs.INTEGER,
            Long.class, KeyCodecs.LONG);

    private static final Map<Type, ScoreCodec<?, ?>> DEFAULT_SCORE_CODECS = Map.of(
            Integer.class, EvaluationCriteria.INTEGER,
            Long.class, EvaluationCriteria.LONG,
            Double.class, EvaluationCriteria.DOUBLE,
            String.class, EvaluationCriteria.STRING);

    private final ScoreProvider<K, V> provider;
    private KeyCodec<K> keyCodec;
    private ScoreCodec<V, ?> scoreCodec;

    @SuppressWarnings("unchecked")
    ProviderConfig(ScoreProvider<K, V> provider) {
        this.provider = provider;
        Type[] arguments = typeArguments(provider.getClass());
        if (arguments != null) {
            keyCodec = (KeyCodec<K>) DEFAULT_KEY_CODECS.get(arguments[0]);
            scoreCodec = (ScoreCodec<V, ?>) DEFAULT_SCORE_CODECS.get(arguments[1]);
        }
    }

    public ProviderConfig<K, V> evaluationCriteria(ScoreCodec<V, ?> codec) {
        scoreCodec = Objects.requireNonNull(codec, "codec");
        return this;
    }

    public ProviderConfig<K, V> keyCodec(KeyCodec<K> codec) {
        keyCodec = Objects.requireNonNull(codec, "codec");
        return this;
    }

    ScoreProvider<K, V> provider() {
        return provider;
    }

    KeyCodec<K> keyCodec() {
        return keyCodec;
    }

    ScoreCodec<V, ?> scoreCodec() {
        return scoreCodec;
    }

    void validate() {
        if (keyCodec == null) {
            throw new IllegalStateException("Provider '" + provider.name() + "' has no key codec, configure one with keyCodec(...)");
        }
        if (scoreCodec == null) {
            throw new IllegalStateException("Provider '" + provider.name() + "' has no score codec, configure one with evaluationCriteria(...)");
        }
    }

    private static Type[] typeArguments(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Type candidate : current.getGenericInterfaces()) {
                if (candidate instanceof ParameterizedType parameterized && parameterized.getRawType() == ScoreProvider.class) {
                    return parameterized.getActualTypeArguments();
                }
            }
        }
        return null;
    }
}
