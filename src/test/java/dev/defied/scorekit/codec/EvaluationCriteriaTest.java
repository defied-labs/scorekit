package dev.defied.scorekit.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationCriteriaTest {

    @Test
    void builtInCodecsRoundTrip() {
        assertEquals(Integer.MIN_VALUE, EvaluationCriteria.INTEGER.decode(EvaluationCriteria.INTEGER.encode(Integer.MIN_VALUE)));
        assertEquals(1L << 53, EvaluationCriteria.LONG.decode(EvaluationCriteria.LONG.encode(1L << 53)));
        assertEquals(-2.5, EvaluationCriteria.DOUBLE.decode(EvaluationCriteria.DOUBLE.encode(-2.5)));
        assertEquals("pizza", EvaluationCriteria.STRING.decode(EvaluationCriteria.STRING.encode("pizza")));
    }

    @Test
    void numericCodecsPreserveOrder() {
        assertTrue(ScoreType.NUMERIC.order().compare(EvaluationCriteria.INTEGER.encode(-3), EvaluationCriteria.INTEGER.encode(2)) < 0);
        assertTrue(ScoreType.NUMERIC.order().compare(EvaluationCriteria.LONG.encode(10L), EvaluationCriteria.LONG.encode(9L)) > 0);
    }

    @Test
    void longOutsideExactRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> EvaluationCriteria.LONG.encode((1L << 53) + 1));
        assertThrows(IllegalArgumentException.class, () -> EvaluationCriteria.LONG.encode(Long.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> EvaluationCriteria.LONG.encode(Long.MAX_VALUE));
    }
}
