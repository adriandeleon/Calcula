package com.calcula.cas.symja;

import com.calcula.cas.CasEngine;
import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Interval arithmetic, which this application does not implement and deliberately does not.
 *
 * <p>Measurements are folded here, in {@code Builtins}, so they work with no engine at all. Intervals
 * are not, and the reason is in the last test: the engine knows that the sine of a range containing
 * a half-turn peaks at one. Reimplementing +−×÷ locally would have shadowed that with something
 * worse, and diverged the moment anybody applied a function to a range.
 */
class IntervalTest {

    private static CasEngine engine;

    @BeforeAll
    static void start() {
        engine = new SymjaEngine();
    }

    private static String eval(String source) throws Exception {
        return Formatter.format(engine.eval(Parser.parse(source)));
    }

    @Test
    void arithmeticCarriesTheBounds() throws Exception {
        // Bracketed, because .. binds looser than +: 1 .. 2 + 1 is the range up to three, which is
        // how it reads and what the next assertion pins.
        assertEquals("2 .. 3", eval("(1 .. 2) + 1"));
        assertEquals("1 .. 3", eval("1 .. 2 + 1"));
        assertEquals("2 .. 4", eval("(1 .. 2) * 2"));
        assertEquals("3 .. 8", eval("(1 .. 2) * (3 .. 4)"));
    }

    @Test
    void aRangeSurvivesTheRoundTripAsARange() throws Exception {
        assertEquals("1 .. 2", eval("1 .. 2"));
    }

    @Test
    void theEngineKnowsWhereAFunctionPeaksOverARange() throws Exception {
        // sin over 1..2 contains pi/2, so the top of the range is exactly 1 — not sin(2). This is
        // what local interval arithmetic would have got wrong, and why there is none.
        assertEquals("sin(1) .. 1", eval("sin(1 .. 2)"));
    }
}
