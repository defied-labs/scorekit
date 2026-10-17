package dev.defied.scorekit.codec;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.UnaryOperator;

public final class ScoreType<S> {

    public static final ScoreType<Double> NUMERIC = new ScoreType<>(Double::compare, ScoreType::normalizeNumeric);
    public static final ScoreType<String> LEXICOGRAPHIC = new ScoreType<>(ScoreType::compareCodePoints, ScoreType::normalizeLexicographic);

    private final Comparator<S> order;
    private final UnaryOperator<S> normalizer;

    private ScoreType(Comparator<S> order, UnaryOperator<S> normalizer) {
        this.order = order;
        this.normalizer = normalizer;
    }

    public Comparator<S> order() {
        return order;
    }

    public S normalize(S score) {
        return normalizer.apply(Objects.requireNonNull(score, "Encoded score must not be null"));
    }

    private static Double normalizeNumeric(Double score) {
        if (score.isNaN()) {
            throw new IllegalArgumentException("Score must not be NaN");
        }
        return score == 0.0 ? 0.0 : score;
    }

    private static String normalizeLexicographic(String score) {
        if (score.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Lexicographic score must not contain \\0");
        }
        return score;
    }

    private static int compareCodePoints(String a, String b) {
        int i = 0;
        while (i < a.length() && i < b.length()) {
            int ca = a.codePointAt(i);
            int cb = b.codePointAt(i);
            if (ca != cb) {
                return Integer.compare(ca, cb);
            }
            i += Character.charCount(ca);
        }
        return Integer.compare(a.length(), b.length());
    }
}
