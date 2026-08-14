package com.calcula.machine;

import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Temperature typed and converted, with no engine anywhere near it — because the engine cannot. */
class TemperatureEvaluationTest {

    private static String evaluated(String source) {
        return Formatter.format(Evaluator.NUMERIC.evaluate(Parser.parse(source), Modes.DEFAULTS));
    }

    @Test
    void theConversionHappensHere() {
        assertEquals("212 degF", evaluated("UnitConvert(100 degC, degF)"));
        assertEquals("0 degC", evaluated("UnitConvert(32 degF, degC)"));
        assertEquals("-40 degF", evaluated("UnitConvert(-40 degC, degF)"));
    }

    /**
     * Anything that is not two temperature scales is left for the engine.
     *
     * <p>This evaluator has no units table, so a length conversion standing here is the correct
     * answer: it stands until the engine sees it. The assertion is that the temperature case did not
     * quietly claim it.
     */
    @Test
    void anOrdinaryConversionIsLeftAlone() {
        assertEquals("UnitConvert(3 ft, m)", evaluated("UnitConvert(3 ft, m)"));
        assertEquals("UnitConvert(3 degC, m)", evaluated("UnitConvert(3 degC, m)"));
    }
}
