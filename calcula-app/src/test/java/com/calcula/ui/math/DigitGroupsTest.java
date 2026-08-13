package com.calcula.ui.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Grouping long numbers, and leaving everything else alone. */
class DigitGroupsTest {

    private static final String T = DigitGroups.THIN_SPACE;

    @Test
    void aLongIntegerIsGrouped() {
        assertEquals(
                "18" + T + "446" + T + "744" + T + "073" + T + "709" + T + "551" + T + "615",
                DigitGroups.group("18446744073709551615"));
    }

    /** The leading group is whatever is left over, so grouping runs from the right. */
    @Test
    void theLeadingGroupIsTheShortOne() {
        assertEquals("1" + T + "000" + T + "000", DigitGroups.group("1000000"));
        assertEquals("12" + T + "345", DigitGroups.group("12345"));
        assertEquals("123" + T + "456", DigitGroups.group("123456"));
    }

    /** A year is four digits and wants to stay one word: "2 026" reads as two numbers. */
    @Test
    void shortNumbersAreLeftAlone() {
        assertEquals("2026", DigitGroups.group("2026"));
        assertEquals("999", DigitGroups.group("999"));
        assertEquals("7", DigitGroups.group("7"));
    }

    @Test
    void aMinusSignIsNotADigit() {
        assertEquals("-12" + T + "345", DigitGroups.group("-12345"));
        assertEquals("-999", DigitGroups.group("-999"));
    }

    /** Only the integer part. Grouping on both sides of the point is harder to read, not easier. */
    @Test
    void theFractionalPartIsLeftAlone() {
        assertEquals("12" + T + "345.6789012", DigitGroups.group("12345.6789012"));
        assertEquals("3.14159265358979", DigitGroups.group("3.14159265358979"));
    }

    /**
     * Anything that is not a number passes through untouched. This is the guard that matters, because
     * the same rendering path draws symbol names.
     */
    @Test
    void anythingThatIsNotANumberIsUntouched() {
        assertEquals("x", DigitGroups.group("x"));
        assertEquals("x123456", DigitGroups.group("x123456"));
        assertEquals("123456x", DigitGroups.group("123456x"), "digits followed by a name is not a number");
        assertEquals("", DigitGroups.group(""));
        assertEquals("-", DigitGroups.group("-"));
    }

    @Test
    void groupingIsStableWhenAppliedTwice() {
        String once = DigitGroups.group("1234567");
        assertEquals(once, DigitGroups.group(once), "a grouped number is no longer a bare digit run");
    }
}
