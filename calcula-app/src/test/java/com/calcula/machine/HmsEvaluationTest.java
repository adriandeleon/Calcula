package com.calcula.machine;

import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Hours, minutes and seconds, typed and worked out with no engine anywhere near it. */
class HmsEvaluationTest {

    private static String evaluated(String source) {
        return Formatter.format(Evaluator.NUMERIC.evaluate(Parser.parse(source), Modes.DEFAULTS));
    }

    private static String roundTrip(String source) {
        return Formatter.format(Parser.parse(source));
    }

    @Test
    void aDurationIsWrittenBackTheWayItWasTyped() {
        assertEquals("1@ 30' 0\"", roundTrip("1@ 30' 0\""));
        assertEquals("12@ 34' 56\"", roundTrip("12@ 34' 56\""));
    }

    /** Any tail of it is a duration too, and comes back in full because all three parts are written. */
    @Test
    void aShorterFormIsStillADuration() {
        assertEquals("1@ 0' 0\"", roundTrip("1@"));
        assertEquals("0@ 30' 0\"", roundTrip("30'"));
        assertEquals("0@ 0' 45\"", roundTrip("45\""));
        assertEquals("1@ 30' 0\"", roundTrip("1@ 30'"));
    }

    @Test
    void durationsAddAndSubtract() {
        assertEquals("2@ 15' 0\"", evaluated("1@ 30' 0\" + 0@ 45' 0\""));
        assertEquals("0@ 45' 0\"", evaluated("1@ 30' 0\" - 0@ 45' 0\""));
    }

    /**
     * The whole form is negated, not its leading part.
     *
     * <p>Read per-part, this comes out an hour wrong and entirely plausible — which is why the sign is
     * asserted through the evaluator as well as in the form's own tests.
     */
    @Test
    void aDurationNegatesAsAWhole() {
        assertEquals("-1@ 30' 0\"", evaluated("-(1@ 30' 0\")"));
        assertEquals("-0@ 45' 0\"", evaluated("0@ 45' 0\" - 1@ 30' 0\""));
    }

    @Test
    void aDurationScalesByANumber() {
        assertEquals("4@ 30' 0\"", evaluated("3 * (1@ 30' 0\")"));
        assertEquals("0@ 30' 0\"", evaluated("(1@ 30' 0\") / 3"));
    }

    /** How many of one fit in the other is a count, and a count is not a time. */
    @Test
    void oneDurationIntoAnotherIsAPlainNumber() {
        assertEquals("48", evaluated("(24@ 0' 0\") / (0@ 30' 0\")"));
    }

    /**
     * Parts over sixty carry, which is the only thing about a duration that is not simple addition.
     *
     * <p>Typed as well as computed: the carry happens at the moment of printing, so a form that was
     * never added to has still been through it.
     */
    @Test
    void partsOverSixtyCarry() {
        assertEquals("1@ 30' 0\"", roundTrip("0@ 90' 0\""));
        assertEquals("2@ 0' 0\"", evaluated("1@ 30' 0\" + 0@ 30' 0\""));
    }

    /**
     * What has no reading on a clock is left standing rather than answered.
     *
     * <p>Two durations multiplied would be an area of time, and a bare number beside a duration might
     * mean hours or might mean minutes. Both come back unevaluated, which is the honest answer and
     * also a decision worth being able to see in a test.
     */
    @Test
    void whatHasNoMeaningIsLeftAlone() {
        assertEquals("1@ 0' 0\"*2@ 0' 0\"", evaluated("(1@ 0' 0\") * (2@ 0' 0\")"));
        assertEquals("1@ 0' 0\" + 30", evaluated("1@ 0' 0\" + 30"));
    }

    /** A duration is a literal, so it sits inside ordinary expressions without brackets appearing. */
    @Test
    void aDurationIsAnAtom() {
        assertEquals("2@ 0' 0\"", evaluated("1@ 0' 0\" + 1@ 0' 0\""));
        assertEquals("1@ 30' 0\" = 1@ 30' 0\"", roundTrip("1@ 30' 0\" == 1@ 30' 0\""));
    }

    /** A part after the first without its marker is a mistake worth naming. */
    @Test
    void aMissingMarkerIsRefused() {
        org.junit.jupiter.api.Assertions.assertThrows(
                com.calcula.parse.ParseException.class, () -> Parser.parse("1@ 30"));
    }
}
