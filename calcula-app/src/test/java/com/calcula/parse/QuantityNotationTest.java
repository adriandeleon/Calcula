package com.calcula.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Reading and writing `3 m`, with no engine anywhere near it. */
class QuantityNotationTest {

    private static String roundTrip(String source) {
        return Formatter.format(Parser.parse(source));
    }

    @Test
    void aNumberBeforeAUnitIsAQuantity() {
        assertEquals("3 m", roundTrip("3 m"));
        assertEquals("100 km", roundTrip("100 km"));
        assertEquals("2.5 kg", roundTrip("2.5 kg"));
    }

    @Test
    void quantitiesSitInsideOrdinaryExpressions() {
        assertEquals("3 m + 2 m", roundTrip("3 m + 2 m"));
        assertEquals("2*3 m", roundTrip("2 * (3 m)"));
    }

    /**
     * A symbol that is not a unit is the parse error it always was.
     *
     * <p>The whole reason claiming {@code NUMBER SYMBOL} costs nothing: there is no implicit
     * multiplication here, so nothing that used to work stops working. This is the assertion that says
     * so, and it would fail the day implicit multiplication arrives — which is the right time to have
     * this conversation again.
     */
    @Test
    void aSymbolThatIsNotAUnitIsStillAnError() {
        assertThrows(ParseException.class, () -> Parser.parse("3 x"));
        assertThrows(ParseException.class, () -> Parser.parse("3 foo"));
    }

    /**
     * A compound unit is printed in the engine's spelling, deliberately.
     *
     * <p>{@code 3 m * 4 m} comes back from the engine as a quantity whose unit is the single name
     * {@code m^2}. Printed bare that is {@code 12 m^2}, which reads back as {@code (12 m)^2} — 144
     * square metres. A wrong answer produced by printing, at save time, with nothing to see. The ugly
     * spelling is the stable one, and this test is here so that the day someone makes compound units
     * print nicely, they have to make them parse too.
     */
    @Test
    void aCompoundUnitKeepsTheEngineSpellingUntilItCanBeReadBack() {
        String printed = roundTrip("Quantity(12, m2)");
        assertEquals("Quantity(12, m2)", printed);
        assertEquals(printed, roundTrip(printed), "and it is stable under a second pass");
    }

    /** Durations still parse: two literals both starting at a number, and they do not collide. */
    @Test
    void aDurationIsStillADuration() {
        assertEquals("1@ 30' 0\"", roundTrip("1@ 30' 0\""));
        assertEquals("3 s", roundTrip("3 s"));
    }
}
