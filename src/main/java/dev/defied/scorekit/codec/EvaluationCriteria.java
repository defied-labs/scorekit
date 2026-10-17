package dev.defied.scorekit.codec;

public final class EvaluationCriteria {

    private static final long MAX_EXACT_LONG = 1L << 53;

    public static final ScoreCodec<Integer, Double> INTEGER = ScoreCodec.of(ScoreType.NUMERIC, Integer::doubleValue, Double::intValue);
    public static final ScoreCodec<Long, Double> LONG = ScoreCodec.of(ScoreType.NUMERIC, EvaluationCriteria::exactDouble, Double::longValue);
    public static final ScoreCodec<Double, Double> DOUBLE = ScoreCodec.of(ScoreType.NUMERIC, value -> value, value -> value);
    public static final ScoreCodec<String, String> STRING = ScoreCodec.of(ScoreType.LEXICOGRAPHIC, value -> value, value -> value);

    private EvaluationCriteria() {
    }

    private static Double exactDouble(Long value) {
        if (value > MAX_EXACT_LONG || value < -MAX_EXACT_LONG) {
            throw new IllegalArgumentException("Long score " + value + " is outside the exact double range ±2^53");
        }
        return value.doubleValue();
    }
}
