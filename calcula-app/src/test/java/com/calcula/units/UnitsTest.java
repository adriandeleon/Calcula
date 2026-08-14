package com.calcula.units;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The closed list of unit names.
 *
 * <p>Closed because the parser has to decide what {@code 3 m} is before anything reaches the engine,
 * and the conversion command checks the same list, so that entry and conversion cannot come to
 * disagree about what counts as a unit.
 */
class UnitsTest {

    @Test
    void theCommonUnitsAreThere() {
        assertTrue(Units.isUnit("m"));
        assertTrue(Units.isUnit("ft"));
        assertTrue(Units.isUnit("kg"));
        assertTrue(Units.isUnit("s"));
    }

    /**
     * A name the engine knows and this does not is refused rather than passed through.
     *
     * <p>Passed through it would convert successfully sometimes and silently do nothing the rest of
     * the time, which is a worse answer than a clear no.
     */
    @Test
    void anythingElseIsNotAUnit() {
        assertFalse(Units.isUnit("x"));
        assertFalse(Units.isUnit("foo"));
        assertFalse(Units.isUnit("furlong"));
        assertFalse(Units.isUnit(""));
    }

    /** Case matters: {@code K} is kelvin, and the engine reads a lower-case one differently. */
    @Test
    void caseIsSignificant() {
        assertTrue(Units.isUnit("K"));
        assertFalse(Units.isUnit("k"));
    }
}
