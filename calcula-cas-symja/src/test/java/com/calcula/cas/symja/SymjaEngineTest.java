package com.calcula.cas.symja;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import com.calcula.expr.Expr;
import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the real engine through the real adapter, in the notation a user would type. Construction
 * costs ~650 ms of static initialisation, so the engine is built once for the class.
 */
class SymjaEngineTest {

    private static CasEngine engine;

    @BeforeAll
    static void start() {
        engine = new SymjaEngine();
    }

    /** Type it, evaluate it, read it back — the whole pipeline in one call. */
    private static String eval(String source) throws CasException {
        return Formatter.format(engine.eval(Parser.parse(source)));
    }

    @Test
    void reportsItself() {
        assertEquals("symja", engine.id());
        assertTrue(engine.available());
    }

    @Test
    void arithmeticStaysExact() throws Exception {
        assertEquals("5", eval("2+3"));
        assertEquals("5/6", eval("1/2 + 1/3"));
        assertEquals("1/3", eval("2/6"));
    }

    @Test
    void symbolicCalculusRoundTripsThroughOurNotation() throws Exception {
        assertEquals("2*x", eval("d(x^2, x)"));
        assertEquals("-x*cos(x) + sin(x)", eval("integrate(x*sin(x), x)"));
        assertEquals("1", eval("limit(sin(x)/x, x -> 0)"));
    }

    @Test
    void solveResultsComeBackAsRules() throws Exception {
        // The whole reason Rule is in the grammar: without it this prints as Rule(x, 1) and cannot be
        // typed back in.
        assertEquals("[[x -> 1], [x -> 2], [x -> 3]]", eval("solve(x^3 - 6*x^2 + 11*x - 6 = 0, x)"));
    }

    @Test
    void anUnknownHeadSurvivesTheRoundTripUntouched() throws Exception {
        // Totality: we model no functions specially, so one we have never heard of still works.
        assertEquals("pi^2/6", eval("Zeta(2)"));
    }

    @Test
    void bigExactIntegersSurvive() throws Exception {
        assertEquals(
                "265252859812191058636308480000000",
                eval("30!"),
                "an exact factorial must not degrade to a double on the way through");
    }

    @Test
    void constantsMapBothWays() throws Exception {
        assertEquals("pi", eval("pi"));
        assertEquals("0", eval("sin(pi)"));
        assertEquals("-1", eval("I^2"));
    }

    @Test
    void inexactInputStaysInexact() throws Exception {
        assertEquals("0.5", eval("1.0/2"));
    }

    @Test
    void texAndMathmlAreRenderedFromTheAnswerNotTheQuestion() throws Exception {
        Expr integral = Parser.parse("integrate(2*x, x)");
        String tex = engine.texForm(integral);
        assertTrue(tex.contains("x") && !tex.contains("int"), tex);

        String mathml = engine.mathmlForm(Parser.parse("(x+1)/(x^2-3)"));
        assertTrue(mathml.contains("<mfrac>"), mathml);
        assertTrue(mathml.contains("<msup>"), mathml);
    }

    @Test
    void matricesConvertAsNestedLists() throws Exception {
        assertEquals("[[1, 2], [3, 4]]", eval("[[1,2],[3,4]]"));
        // `*` is ELEMENTWISE, as in Mathematica; the matrix product is dot().
        assertEquals("[[5, 12], [21, 32]]", eval("[[1,2],[3,4]] * [[5,6],[7,8]]"));
        assertEquals("[[19, 22], [43, 50]]", eval("dot([[1,2],[3,4]], [[5,6],[7,8]])"));
    }

    @Test
    void nothingToEvaluateIsReportedRatherThanThrowingFromInside() {
        assertThrows(CasException.class, () -> engine.eval(null));
    }

    @Test
    void anUnmodelledEngineValueRoundTripsThroughItsPrintedForm() throws Exception {
        // Complex numbers are not a leaf in our tree. The $Engine escape keeps them exact rather than
        // losing them, and re-parsing goes back through the engine that printed them.
        Expr complex = engine.eval(Parser.parse("(1 + 2*I) * (3 + 4*I)"));
        // It displays in the engine's own notation rather than ours — honest, and readable. What must
        // hold is that feeding it back in is a fixed point rather than a slow corruption.
        assertEquals("-5+I*10", Formatter.format(complex));
        assertEquals(
                Formatter.format(complex),
                Formatter.format(engine.eval(complex)),
                "re-evaluating an engine value must be stable");
    }

    @Test
    void applyEncodingSurvivesAHeadThatIsItselfAnExpression() throws Exception {
        // Derivative[1][f] has an expression for a head, which a String-headed Call cannot hold
        // directly; $Apply carries it as the first argument.
        Expr derivative = engine.eval(Parser.parse("d(f(x), x)"));
        String again = Formatter.format(engine.eval(derivative));
        assertEquals(Formatter.format(derivative), again, "a re-evaluated derivative must be stable");
    }
}
