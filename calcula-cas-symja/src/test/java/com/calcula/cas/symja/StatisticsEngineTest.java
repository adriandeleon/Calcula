package com.calcula.cas.symja;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The statistics the commands reach.
 *
 * <p>Asserted through the pipeline rather than by checking a command exists, because what makes these
 * worth having is that the answers come back exact — and exactness is the engine's, not the command's.
 */
class StatisticsEngineTest {

    private static CasEngine engine;

    @BeforeAll
    static void start() {
        engine = new SymjaEngine();
    }

    private static String eval(String source) throws CasException {
        return Formatter.format(engine.eval(Parser.parse(source)));
    }

    /** 5/2, not 2.5, and a real square root, not 1.2909944487. */
    @Test
    void theAnswersAreExact() throws CasException {
        assertEquals("5/2", eval("Mean([1, 2, 3, 4])"));
        assertEquals("5/2", eval("Median([1, 2, 3, 4])"));
        assertEquals("5/3", eval("Variance([1, 2, 3, 4])"));
        // The engine says Sqrt(5/3); this is that expression in the notation this application writes.
        assertEquals("(5/3)^(1/2)", eval("StandardDeviation([1, 2, 3, 4])"));
        assertEquals("10", eval("Total([1, 2, 3, 4])"));
    }

    @Test
    void twoListsCompare() throws CasException {
        assertEquals("5/2", eval("Covariance([1, 2, 3], [2, 4, 7])"));
    }

    /**
     * A fit comes back as a formula, which is the point.
     *
     * <p>It lands on the stack as an expression — typeset by this window, and plottable beside the
     * data it came from. That is what makes curve fitting worth more here than a printed number.
     */
    @Test
    void aFitComesBackAsRules() throws CasException {
        String fitted = eval("FindFit([[1, 2], [2, 4], [3, 7]], a*x + b, [a, b], x)");
        org.junit.jupiter.api.Assertions.assertTrue(
                fitted.startsWith("[a -> 2.5"), "the slope should be two and a half: " + fitted);
    }
}
