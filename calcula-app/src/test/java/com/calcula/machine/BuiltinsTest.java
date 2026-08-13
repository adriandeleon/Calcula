package com.calcula.machine;

import java.util.List;

import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The functions this calculator implements itself, reached the way a user reaches them — typed, in
 * the friendly spelling, through the evaluator that runs when there is no engine at all.
 */
class BuiltinsTest {

    private static String evaluated(String source) {
        return Formatter.format(Evaluator.NUMERIC.evaluate(Parser.parse(source), Modes.DEFAULTS));
    }

    private static String cents(String source) {
        return new java.math.BigDecimal(evaluated(source))
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString();
    }

    @Test
    void theSpreadsheetSpellingsReachTheFormulas() {
        assertEquals("-1199.10", cents("pmt(0.005, 360, 200000, 0)"));
        assertEquals("16387.93", cents("fv(0.005, 120, -100, 0)"));
        assertEquals("1800.00", cents("sln(10000, 1000, 5)"));
    }

    @Test
    void theyWorkWithNoEngineAtAll() {
        // Evaluator.NUMERIC is the no-engine path, and that is what this whole test runs through.
        // Money is arithmetic; waiting for a CAS to load before it can be done would be absurd.
        assertEquals("243.43", cents("npv(0.10, [-1000, 500, 500, 500])"));
    }

    @Test
    void theyFoldInsideALargerExpression() {
        // The reason this sits in the numeric fold rather than beside it: pv(...) + 50 has to work
        // like sqrt(4) + 50, bottom-up, with no special case anywhere.
        assertEquals("1850.00", cents("sln(10000, 1000, 5) + 50"));
    }

    @Test
    void aSymbolicArgumentLeavesTheCallStanding() {
        // Not an error. Every other unfinished expression here stands unevaluated, and one that
        // shouted instead would interrupt somebody halfway through typing.
        assertEquals("sln(10000, 1000, n)", evaluated("sln(10000, 1000, n)"));
    }

    @Test
    void theWrongNumberOfArgumentsAlsoJustStands() {
        assertEquals("sln(10000, 1000)", evaluated("sln(10000, 1000)"));
    }

    @Test
    void anAnswerThatCannotBeComputedLeavesTheCallStandingToo() {
        // A rate that cannot balance. The message belongs to whoever asked, not to a fold.
        assertEquals("rate(10, 1, 1, 1)", evaluated("rate(10, 1, 1, 1)"));
    }

    @Test
    void aHeadNobodyHereKnowsIsUntouched() {
        // The property that keeps this a layer rather than a fork: Builtins answers null and the
        // expression carries on to the engine exactly as it always did.
        assertEquals(null, Builtins.apply("Integrate", List.of(), Modes.DEFAULTS.mathContext()));
    }
}
