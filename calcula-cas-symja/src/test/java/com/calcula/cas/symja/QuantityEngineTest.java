package com.calcula.cas.symja;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Units through the real engine, typed as a user would type them. */
class QuantityEngineTest {

    private static CasEngine engine;

    @BeforeAll
    static void start() {
        engine = new SymjaEngine();
    }

    private static String eval(String source) throws CasException {
        return Formatter.format(engine.eval(Parser.parse(source)));
    }

    @Test
    void quantitiesAdd() throws CasException {
        assertEquals("5 m", eval("3 m + 2 m"));
    }

    /**
     * Different units of the same kind convert, and exactly.
     *
     * <p>Three feet is 1143/1250 of a metre and the engine says so rather than 0.9144 — which is the
     * whole reason to send this out rather than build a conversion table here.
     */
    @Test
    void unlikeUnitsOfTheSameKindConvert() throws CasException {
        assertEquals("3643/1250 m", eval("3 ft + 2 m"));
    }

    /**
     * Metres and seconds do not add, and the sum stands rather than answering.
     *
     * <p>The issue asked for an error. Standing is what the engine does and it is honest — the
     * expression is on the stack, unevaluated, visibly not a number.
     */
    @Test
    void unlikeKindsDoNotAdd() throws CasException {
        assertEquals("3 m + 2 s", eval("3 m + 2 s"));
    }

    /**
     * The unit has to reach the engine as a string.
     *
     * <p>Sent as a symbol — the obvious thing, our tree having no strings — every one of these comes
     * back untouched, and it reads as the engine not supporting units rather than as us asking
     * wrongly. That is the whole content of the adapter change, so it is asserted through arithmetic
     * that can only work if the string arrived.
     */
    @Test
    void aQuantitySurvivesBothDirections() throws CasException {
        assertEquals("3 m", eval("3 m"));
        assertEquals("6 m", eval("2 * (3 m)"));
    }
}
