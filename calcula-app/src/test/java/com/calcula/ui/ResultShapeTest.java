package com.calcula.ui;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Reading a result as the thing it is.
 *
 * <p>The shapes here were taken from the real engine rather than guessed: {@code FactorInteger(1)} is
 * {@code [[1, 1]]}, and factoring a negative really does come back with a {@code -1} out in front.
 */
class ResultShapeTest {

    private static final Expr FACTORED = Parser.parse("FactorInteger(12)");

    private static String reading(String value, Expr origin) {
        Expr shaped = ResultShape.reading(Parser.parse(value), origin);
        return shaped == null ? null : Formatter.format(shaped);
    }

    @Test
    void aFactorisationIsSetAsAProduct() {
        assertEquals("2^2*3", reading("[[2, 2], [3, 1]]", FACTORED));
    }

    /** An exponent of one is dropped: 3^1*5^1 is a worse reading of the same number than 3*5. */
    @Test
    void exponentsOfOneAreNotWritten() {
        assertEquals(
                "3*5*17*257*641*65537*6700417",
                reading("[[3, 1], [5, 1], [17, 1], [257, 1], [641, 1], [65537, 1], [6700417, 1]]", FACTORED));
    }

    @Test
    void oneFactorIsJustThatFactor() {
        assertEquals("7", reading("[[7, 1]]", FACTORED), "FactorInteger(7) is 7, not a product of one thing");
    }

    /**
     * What the engine really returns for a negative, checked against it rather than assumed — and
     * then handed to a formatter that folds the leading -1 into a minus sign, which is the better
     * reading and the reason nothing here special-cases it.
     */
    @Test
    void aNegativeIsReadAsANegativeProduct() {
        assertEquals("-2^2*3", reading("[[-1, 1], [2, 2], [3, 1]]", FACTORED));
    }

    @Test
    void factoringOneIsOne() {
        assertEquals("1", reading("[[1, 1]]", FACTORED));
    }

    // ---- the refusals, which are the point -------------------------------------------------

    /**
     * The same value with no history is left alone. This is the case that could not be decided before
     * provenance: it is genuinely indistinguishable from a 2×2 matrix somebody typed.
     */
    @Test
    void theSameValueFromNowhereIsLeftAlone() {
        assertNull(reading("[[2, 2], [3, 1]]", null));
    }

    @Test
    void theSameValueFromSomethingElseIsLeftAlone() {
        assertNull(reading("[[2, 2], [3, 1]]", Parser.parse("[[2, 2], [3, 1]]")), "typed, not factored");
        assertNull(reading("[[2, 2], [3, 1]]", Parser.parse("Transpose([[2, 3], [2, 1]])")));
    }

    @Test
    void anythingNotShapedLikeAFactorisationIsLeftAlone() {
        assertNull(reading("[[2, 2], [3]]", FACTORED), "a pair that is not a pair");
        assertNull(reading("[[2, 2], 3]", FACTORED), "an element that is not a pair at all");
        assertNull(reading("[[x, 2]]", FACTORED), "a base that is not an integer");
        assertNull(reading("[[2, 0]]", FACTORED), "an exponent FactorInteger would never produce");
        assertNull(reading("42", FACTORED), "not a list");
    }

    @Test
    void anEmptyListHasNothingToRead() {
        assertNull(ResultShape.reading(Exprs.list(), FACTORED));
    }
}
