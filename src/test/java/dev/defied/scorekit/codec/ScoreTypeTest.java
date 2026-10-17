package dev.defied.scorekit.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreTypeTest {

    @Test
    void numericRejectsNaN() {
        assertThrows(IllegalArgumentException.class, () -> ScoreType.NUMERIC.normalize(Double.NaN));
    }

    @Test
    void numericNormalizesNegativeZero() {
        assertEquals(0, ScoreType.NUMERIC.order().compare(ScoreType.NUMERIC.normalize(-0.0), 0.0));
    }

    @Test
    void lexicographicRejectsNullCharacter() {
        assertThrows(IllegalArgumentException.class, () -> ScoreType.LEXICOGRAPHIC.normalize("a\0b"));
    }

    @Test
    void lexicographicOrdersByCodePoint() {
        String bmp = "￿";
        String supplementary = "😀";
        assertTrue(bmp.compareTo(supplementary) > 0);
        assertTrue(ScoreType.LEXICOGRAPHIC.order().compare(bmp, supplementary) < 0);
    }

    @Test
    void lexicographicOrdersPrefixFirst() {
        assertTrue(ScoreType.LEXICOGRAPHIC.order().compare("ab", "abc") < 0);
        assertEquals(0, ScoreType.LEXICOGRAPHIC.order().compare("abc", "abc"));
    }
}
