package com.calcula.units;

import java.math.BigDecimal;
import java.math.BigInteger;

import com.calcula.expr.Arith;
import com.calcula.expr.Expr.Num;
import com.calcula.expr.Exprs;
import com.calcula.parse.Formatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The conversion the engine will not do.
 *
 * <p>Asserted on the printed form, because the point of doing this in rationals is that the answers
 * come out as the numbers people already know them to be.
 */
class TemperatureTest {

    private static String convert(String value, String from, String to) {
        Num v = value.contains(".") ? Exprs.of(new BigDecimal(value)) : Exprs.of(new BigInteger(value));
        return Formatter.format(Temperature.convert(v, from, to));
    }

    /** The four everybody knows. Any of these coming out as 211.99999999 would be the whole bug. */
    @Test
    void theAnswersPeopleAlreadyKnow() {
        assertEquals("212", convert("100", "degC", "degF"));
        assertEquals("32", convert("0", "degC", "degF"));
        assertEquals("0", convert("32", "degF", "degC"));
        assertEquals("-40", convert("-40", "degC", "degF"));
    }

    /**
     * A decimal in, a decimal out.
     *
     * <p>{@code 98.6} typed is inexact, and inexactness is contagious through {@link
     * com.calcula.expr.Arith} by design, so the answer is 37 carrying the working precision rather
     * than the bare 37 an exact input gives. Compared by value for that reason — asserting the printed
     * form here would be asserting the precision setting.
     */
    @Test
    void aDecimalReadingConvertsToADecimal() {
        Num answer = Temperature.convert(Exprs.of(new BigDecimal("98.6")), "degF", "degC");
        assertEquals(0, Arith.compare(answer, Exprs.of(new BigInteger("37"))));
    }

    /**
     * The offset is exact, and exact means a fraction.
     *
     * <p>273.15 is 5463/20, and that is what comes out — the same shape the engine returns for an
     * ordinary conversion, where three feet plus two metres is {@code 3643/1250 m}. Turning it into
     * 273.15 for display is what fraction mode is for, and doing it here instead would make
     * temperature the one conversion that answers differently from all the others.
     */
    @Test
    void kelvinCarriesTheOffsetExactly() {
        assertEquals("5463/20", convert("0", "degC", "K"));
        assertEquals("-5463/20", convert("0", "K", "degC"));
        assertEquals(
                0,
                Arith.compare(
                        Temperature.convert(Exprs.of(new BigDecimal("273.15")), "K", "degC"),
                        Exprs.of(BigInteger.ZERO)));
    }

    /** Rankine is to Fahrenheit what Kelvin is to Celsius, and absolute zero is the check. */
    /** Rankine is to Fahrenheit what Kelvin is to Celsius, and absolute zero is the check. */
    @Test
    void rankineIsAbsoluteFahrenheit() {
        assertEquals("49167/100", convert("0", "degC", "degR")); // 491.67
        assertEquals("0", convert("0", "K", "degR"));
    }

    /** Through Kelvin and back is the identity, on every pair, which the twelve-formula version is not. */
    @Test
    void everyPairRoundTrips() {
        String[] scales = {"K", "degC", "degF", "degR"};
        for (String from : scales) {
            for (String to : scales) {
                Num start = Exprs.of(new BigInteger("100"));
                Num there = Temperature.convert(start, from, to);
                Num back = Temperature.convert(there, to, from);
                assertEquals("100", Formatter.format(back), from + " to " + to + " and back");
            }
        }
    }

    @Test
    void onlyTemperatureScalesAreTemperatures() {
        assertTrue(Temperature.isTemperature("degC"));
        assertTrue(Temperature.isTemperature("K"));
        assertFalse(Temperature.isTemperature("m"));
        assertFalse(Temperature.isConversion("degC", "m"));
        assertThrows(IllegalArgumentException.class, () -> Temperature.convert(Exprs.of(BigInteger.ONE), "degC", "m"));
    }
}
