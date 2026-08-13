package com.calcula.machine;

import java.math.BigDecimal;

import com.calcula.machine.FloatFormat.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FloatFormatTest {

    private static String shown(Style style, int digits, String value) {
        return new FloatFormat(style, digits).format(new BigDecimal(value));
    }

    @Test
    void normalIsEveryDigitTheValueCarries() {
        // The existing behaviour, unchanged: this is what a calculator arguing for exactness owes.
        assertEquals("3.14159265358979", shown(Style.NORMAL, 4, "3.14159265358979"));
        assertEquals("0.00000000001", shown(Style.NORMAL, 4, "0.00000000001"));
    }

    @Test
    void fixedRoundsToTheGivenPlaces() {
        assertEquals("3.1416", shown(Style.FIXED, 4, "3.14159265"));
        assertEquals("3", shown(Style.FIXED, 0, "3.4"));
        assertEquals("2.50", shown(Style.FIXED, 2, "2.5"));
    }

    @Test
    void fixedPadsAsWellAsRounds() {
        // A fixed format that only shortened would be a rounding rule, not a column of numbers.
        assertEquals("1.000000", shown(Style.FIXED, 6, "1"));
    }

    @Test
    void scientificPutsOneDigitBeforeThePoint() {
        assertEquals("1.2346e5", shown(Style.SCIENTIFIC, 4, "123456"));
        assertEquals("1.2346e-5", shown(Style.SCIENTIFIC, 4, "0.0000123456"));
        assertEquals("1.0000e0", shown(Style.SCIENTIFIC, 4, "1"));
    }

    @Test
    void engineeringKeepsTheExponentAMultipleOfThree() {
        assertEquals("123.456e3", shown(Style.ENGINEERING, 3, "123456"));
        assertEquals("1.235e6", shown(Style.ENGINEERING, 3, "1234567"));
        assertEquals("12.346e-6", shown(Style.ENGINEERING, 3, "0.0000123456"));
        assertEquals("100.000e0", shown(Style.ENGINEERING, 3, "100"));
    }

    @Test
    void aCarryOutOfTheMantissaIsCorrectedRatherThanShown() {
        // 9.99 to one place is 10.0, which is not a mantissa. The exponent moves instead.
        assertEquals("1.0e1", shown(Style.SCIENTIFIC, 1, "9.99"));
        assertEquals("1.0e-2", shown(Style.SCIENTIFIC, 1, "0.00999"));
        // Engineering carries at a thousand rather than at ten, so 999.9 is still a mantissa and
        // 999.99 rounded to one place is not.
        assertEquals("999.9e0", shown(Style.ENGINEERING, 1, "999.9"));
        assertEquals("1.0e3", shown(Style.ENGINEERING, 1, "999.99"));
    }

    @Test
    void negativesKeepTheirSignAndTheirShape() {
        assertEquals("-3.1416", shown(Style.FIXED, 4, "-3.14159265"));
        assertEquals("-1.2346e5", shown(Style.SCIENTIFIC, 4, "-123456"));
        assertEquals("-1.0e1", shown(Style.SCIENTIFIC, 1, "-9.99"));
    }

    @Test
    void zeroIsZeroRatherThanZeroTimesAPowerOfTen() {
        assertEquals("0.0000", shown(Style.SCIENTIFIC, 4, "0"));
        assertEquals("0.000", shown(Style.ENGINEERING, 3, "0.00"));
        assertEquals("0.00", shown(Style.FIXED, 2, "0"));
    }

    @Test
    void theModeLineSaysNothingAboutTheDefault() {
        assertEquals("", FloatFormat.NORMAL.describe());
        assertEquals("fix 4", new FloatFormat(Style.FIXED, 4).describe());
        assertEquals("sci 6", new FloatFormat(Style.SCIENTIFIC, 6).describe());
        assertEquals("eng 3", new FloatFormat(Style.ENGINEERING, 3).describe());
    }

    @Test
    void aStyleIsFoundByTheWordItIsSavedAs() {
        assertEquals(Style.SCIENTIFIC, Style.byId("scientific"));
        assertEquals(Style.FIXED, Style.byId("FIXED"));
        assertEquals(null, Style.byId("nonsense"));
    }

    @Test
    void digitsOutsideTheRangeAreRefusedRatherThanClamped() {
        assertThrows(IllegalArgumentException.class, () -> new FloatFormat(Style.FIXED, -1));
        assertThrows(IllegalArgumentException.class, () -> new FloatFormat(Style.FIXED, FloatFormat.MAX_DIGITS + 1));
    }
}
