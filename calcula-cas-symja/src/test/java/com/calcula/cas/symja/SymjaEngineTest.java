package com.calcula.cas.symja;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the real engine. Construction costs ~650 ms of static initialisation, so it is built once for
 * the class rather than per test.
 */
class SymjaEngineTest {

    private static CasEngine engine;

    @BeforeAll
    static void start() {
        engine = new SymjaEngine();
    }

    @Test
    void reportsItself() {
        assertEquals("symja", engine.id());
        assertTrue(engine.available());
    }

    @Test
    void evaluatesArithmeticExactly() throws Exception {
        assertEquals("5", engine.eval("2+3"));
        // Exact rationals, not doubles — the whole reason for a CAS rather than a float calculator.
        assertEquals("5/6", engine.eval("1/2 + 1/3"));
    }

    @Test
    void doesSymbolicCalculus() throws Exception {
        assertEquals("2*x", engine.eval("D(x^2, x)"));
        assertEquals("-x*Cos(x)+Sin(x)", engine.eval("Integrate(x*Sin(x), x)"));
    }

    @Test
    void texFormFormatsTheResultRatherThanTheInput() throws Exception {
        // The trap this pins: formatting the unevaluated source instead would render an integral sign
        // here rather than the solved antiderivative.
        String tex = engine.texForm("Integrate(2*x, x)");
        assertTrue(tex.contains("x") && tex.contains("2"), tex);
        assertTrue(tex.contains("{x}^{2}") || tex.contains("x^2"), tex);
    }

    @Test
    void emitsPresentationMathml() throws Exception {
        String mathml = engine.mathmlForm("(x+1)/(x^2-3)");
        assertTrue(mathml.contains("<mfrac>"), mathml);
        assertTrue(mathml.contains("<msup>"), mathml);
    }

    @Test
    void reportsBadInputAsCasExceptionRatherThanLettingRuntimeErrorsEscape() {
        assertThrows(CasException.class, () -> engine.eval("("));
        assertThrows(CasException.class, () -> engine.eval("   "));
    }
}
