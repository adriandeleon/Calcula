package com.calcula.ui;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which values get a decimal in the margin, and which are left alone.
 *
 * <p>The deciding is the expensive part to get wrong: every yes that should have been a no is a round
 * trip on a thread the calculator needs, spent to learn nothing.
 */
class ApproximationTest {

    @Test
    void aRatioIsWorthIt() {
        assertTrue(Approximation.worth(Parser.parse("5/6")));
    }

    @Test
    void aClosedFormOfConstantsIsWorthIt() {
        assertTrue(Approximation.worth(Parser.parse("Pi^2/6")));
        assertTrue(Approximation.worth(Parser.parse("2^(1/2)")));
    }

    /** An integer IS its decimal, so the margin would be repeating the value beside itself. */
    @Test
    void anIntegerIsNot() {
        assertFalse(Approximation.worth(Exprs.of(7)));
    }

    /** Already approximate, and already wearing the amber rail, which says the honest thing. */
    @Test
    void somethingAlreadyInexactIsNot() {
        assertFalse(Approximation.worth(Parser.parse("1.5")));
        assertFalse(Approximation.worth(Parser.parse("1.5 + Pi")));
    }

    /**
     * The refusal that saves the round trips. {@code x + 1} has no decimal — asking gets {@code 1 + x}
     * back, which is a question answered at the cost of learning nothing.
     */
    @Test
    void anythingWithAFreeSymbolIsNot() {
        assertFalse(Approximation.worth(Parser.parse("x + 1")));
        assertFalse(Approximation.worth(Parser.parse("sin(x)")));
        assertFalse(Approximation.worth(Parser.parse("Pi*x")));
    }

    @Test
    void aListIsNot() {
        assertFalse(Approximation.worth(Parser.parse("[1/2, 1/3]")), "a margin repeating a vector is a second stack");
    }

    // ---- the decimal itself ----------------------------------------------------------------

    /**
     * A ratio needs no engine, and that matters: exact arithmetic works without one, so knowing how
     * big the answer is should too.
     *
     * <p>Built rather than parsed, and the difference is the point: {@code parse("5/6")} is
     * {@code Divide(5, 6)}, a call that has not been worked out yet. A Rat is what lands on the
     * STACK, which is the only place this is ever asked.
     */
    @Test
    void aRatioIsWorkedOutWithoutTheEngine() {
        assertEquals("≈ 0.833333333333", Approximation.direct(Exprs.rat(5, 6), 12));
    }

    /** The precision is a ceiling on what can be known, not a demand that every digit be shown. */
    @Test
    void anExactHalfIsNotPaddedWithZeroes() {
        assertEquals("≈ 0.5", Approximation.direct(Exprs.rat(1, 2), 12));
    }

    /**
     * The unevaluated form has to reach the engine, because BigDecimal cannot divide a call.
     * Worth stating: the same arithmetic reads two ways depending on whether it has been done yet.
     */
    @Test
    void anUnevaluatedDivisionGoesToTheEngine() {
        assertNull(Approximation.direct(Parser.parse("5/6"), 12));
        assertNotNull(Approximation.request(Parser.parse("5/6"), 12));
    }

    @Test
    void anythingElseIsLeftToTheEngine() {
        assertNull(Approximation.direct(Parser.parse("Pi^2/6"), 12), "not something BigDecimal can do");
        assertNotNull(Approximation.request(Parser.parse("Pi^2/6"), 12), "so it becomes a question");
    }

    @Test
    void aValueNotWorthItIsNeverEvenAsked() {
        assertNull(Approximation.request(Parser.parse("x + 1"), 12));
        assertNull(Approximation.request(Exprs.of(7), 12));
    }

    // ---- reading the engine's answer --------------------------------------------------------

    @Test
    void aNumericAnswerIsShown() {
        assertEquals("≈ 1.5", Approximation.shown(Exprs.of(new java.math.BigDecimal("1.5"))));
    }

    /**
     * {@code N} is total, so an answer that is still not a number is the engine saying this value has
     * no decimal after all. The margin says nothing rather than echoing the value.
     */
    @Test
    void anAnswerThatIsStillSymbolicShowsNothing() {
        Expr unchanged = Parser.parse("1 + x");
        assertNull(Approximation.shown(unchanged));
    }

    @Test
    void anIntegerAnswerShowsNothingEither() {
        assertNull(Approximation.shown(Exprs.of(3)), "3 beside 3 is noise");
    }
}
