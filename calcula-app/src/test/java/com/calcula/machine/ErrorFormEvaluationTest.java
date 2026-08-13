package com.calcula.machine;

import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Measurements, typed and worked out — with no engine anywhere near it. */
class ErrorFormEvaluationTest {

    private static String evaluated(String source) {
        return Formatter.format(Evaluator.NUMERIC.evaluate(Parser.parse(source), Modes.DEFAULTS));
    }

    private static String roundTrip(String source) {
        return Formatter.format(Parser.parse(source));
    }

    @Test
    void aMeasurementIsWrittenBackTheWayItWasTyped() {
        assertEquals("2 +/- 0.1", roundTrip("2 +/- 0.1"));
        assertEquals("x +/- 0.5", roundTrip("x +/- 0.5"));
    }

    @Test
    void itBindsLooserThanASumAndTighterThanAComparison() {
        // 2 + 3 +/- 0.1 is a measurement of five, not two plus a measurement of three.
        assertEquals("5 +/- 0.1", evaluated("2 + 3 +/- 0.1"));
        assertEquals("2 +/- 0.1 = 2 +/- 0.1", roundTrip("2 +/- 0.1 == 2 +/- 0.1"), "and it is bracketed nowhere");
    }

    @Test
    void arithmeticPropagatesTheError() {
        assertEquals("5 +/- 0.22360679775", evaluated("(2 +/- 0.1) + (3 +/- 0.2)"));
        assertEquals("6 +/- 0.5", evaluated("(2 +/- 0.1) * (3 +/- 0.2)"));
    }

    @Test
    void aPlainNumberIsAMeasurementWithNoError() {
        // So mixing the two needs no separate case anywhere.
        assertEquals("5 +/- 0.1", evaluated("(2 +/- 0.1) + 3"));
        assertEquals("4 +/- 0.2", evaluated("2 * (2 +/- 0.1)"));
    }

    @Test
    void anErrorThatHasGoneLeavesAnOrdinaryNumber() {
        // Carrying `+/- 0` around would be noise, and 0 * x really has no uncertainty.
        assertEquals("0", evaluated("0 * (2 +/- 0.1)"));
    }

    @Test
    void ordinaryArithmeticIsUntouched() {
        // The measurement path answers null the moment nothing is a measurement, which is every sum
        // in the calculator.
        assertEquals("5", evaluated("2 + 3"));
        assertEquals("5/6", evaluated("1/2 + 1/3"));
        assertEquals("x + 1", evaluated("x + 1"));
    }

    @Test
    void aSymbolInsideAMeasurementLeavesTheWholeThingStanding() {
        assertEquals(
                "(2 +/- 0.1)*y", evaluated("(2 +/- 0.1) * y"), "bracketed, because it binds looser than a product");
    }

    @Test
    void itWorksWithNoEngineAtAll() {
        // Everything above runs through Evaluator.NUMERIC, which never consults one. A laboratory
        // number should not wait for a CAS to warm up.
        assertEquals("1 +/- 0.141421356237", evaluated("(3 +/- 0.1) - (2 +/- 0.1)"));
    }
}
