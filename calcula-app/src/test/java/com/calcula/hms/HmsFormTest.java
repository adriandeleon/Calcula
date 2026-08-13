package com.calcula.hms;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HmsFormTest {

    private static final MathContext MC = new MathContext(12);

    private static HmsForm hms(long h, long m, String s) {
        return HmsForm.of(BigInteger.valueOf(h), BigInteger.valueOf(m), new BigDecimal(s));
    }

    @Test
    void thePartsComeBackOutAsTheyWentIn() {
        assertEquals("1@ 30' 0\"", hms(1, 30, "0").format());
        assertEquals("0@ 0' 0\"", hms(0, 0, "0").format());
        assertEquals("12@ 34' 56\"", hms(12, 34, "56").format());
    }

    /**
     * Sixty is a display fact, so anything over it carries.
     *
     * <p>Worth writing rather than assuming: a form built by arithmetic is a total in seconds and has
     * never been anywhere near a boundary, so the only thing that can get this wrong is the printing.
     */
    @Test
    void partsOverSixtyCarry() {
        assertEquals("1@ 30' 0\"", hms(0, 90, "0").format());
        assertEquals("1@ 0' 0\"", hms(0, 0, "3600").format());
        assertEquals("1@ 0' 0\"", hms(0, 59, "60").format());
        assertEquals("2@ 1' 1\"", hms(1, 60, "61").format());
    }

    /**
     * The sign belongs to the whole form.
     *
     * <p>The trap this type exists to avoid: read per-field, {@code -1@ 30' 0"} is minus an hour plus
     * half an hour, which is −1800 seconds and looks entirely plausible. It is −5400.
     */
    @Test
    void aNegativeFormIsNegativeThroughout() {
        HmsForm negated = hms(1, 30, "0").negate();
        assertEquals(BigDecimal.valueOf(-5400), negated.totalSeconds());
        assertEquals("-1@ 30' 0\"", negated.format());
    }

    /** Under an hour, the minus still has to appear — and it has nowhere to sit but a zero hour. */
    @Test
    void aNegativeFormUnderAnHourStillShowsItsSign() {
        assertEquals("-0@ 30' 0\"", hms(0, 30, "0").negate().format());
        assertEquals("-0@ 0' 1\"", hms(0, 0, "1").negate().format());
    }

    @Test
    void durationsAddAndSubtract() {
        assertEquals("2@ 15' 0\"", hms(1, 30, "0").add(hms(0, 45, "0")).format());
        assertEquals("0@ 45' 0\"", hms(1, 30, "0").subtract(hms(0, 45, "0")).format());
        assertEquals("-0@ 45' 0\"", hms(0, 45, "0").subtract(hms(1, 30, "0")).format());
    }

    @Test
    void aDurationScalesByAPlainNumber() {
        assertEquals(
                "4@ 30' 0\"", hms(1, 30, "0").scale(new BigDecimal("3"), MC).format());
        assertEquals(
                "0@ 30' 0\"", hms(1, 30, "0").divideBy(new BigDecimal("3"), MC).format());
    }

    /** Two durations divided is how many of one fit in the other — a count, not a duration. */
    @Test
    void oneDurationIntoAnotherIsAPlainNumber() {
        assertEquals(0, new BigDecimal("48").compareTo(hms(24, 0, "0").ratio(hms(0, 30, "0"), MC)));
    }

    @Test
    void dividingByNothingIsRefused() {
        assertThrows(ArithmeticException.class, () -> hms(1, 0, "0").divideBy(BigDecimal.ZERO, MC));
        assertThrows(ArithmeticException.class, () -> hms(1, 0, "0").ratio(hms(0, 0, "0"), MC));
    }

    /** Fractional seconds survive, and the remainder is taken rather than the whole part dropped. */
    @Test
    void fractionalSecondsAreKept() {
        assertEquals("0@ 1' 30.5\"", hms(0, 0, "90.5").format());
        assertEquals("0@ 0' 0.25\"", hms(0, 0, "0.25").format());
    }

    /**
     * A duration produced by arithmetic prints its seconds cleanly.
     *
     * <p>{@code BigDecimal} division at twelve digits leaves a long tail of zeros, and a clock that
     * reads {@code 0.000000000000"} is not one anybody would keep.
     */
    @Test
    void arithmeticDoesNotLeaveATailOfZeros() {
        assertEquals(
                "0@ 30' 0\"", hms(1, 0, "0").divideBy(new BigDecimal("2"), MC).format());
    }

    @Test
    void hoursConvertBothWays() {
        assertEquals(0, new BigDecimal("1.5").compareTo(hms(1, 30, "0").toHours(MC)));
        assertEquals("1@ 30' 0\"", HmsForm.fromHours(new BigDecimal("1.5")).format());
    }
}
