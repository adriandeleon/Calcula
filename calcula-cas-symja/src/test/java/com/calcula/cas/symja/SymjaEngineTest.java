package com.calcula.cas.symja;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import com.calcula.expr.Expr;
import com.calcula.expr.ExprPath;
import com.calcula.machine.Evaluator;
import com.calcula.machine.Machine;
import com.calcula.machine.Modes;
import com.calcula.machine.Op;
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
        assertEquals("2*x", eval("deriv(x^2, x)"));
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
    void inexactResultsKeepFullPrecision() throws Exception {
        // Symja's Num.toString() is a display form of about six significant figures. Reading the value
        // from it truncated every inexact result in the application, and 1.41421 looks entirely
        // plausible — which is what made it worth pinning.
        assertEquals("1.4142135623730951", eval("N(sqrt(2))"));
        assertTrue(eval("N(pi)").startsWith("3.14159265358979"), eval("N(pi)"));
    }

    @Test
    void arbitraryPrecisionSurvivesToo() throws Exception {
        // The opposite case: here the printed form is the only place the extra digits exist, and
        // taking the double would throw them away.
        String pi = eval("N(pi, 40)");
        assertTrue(pi.startsWith("3.14159265358979323846264338327950288419"), pi);
        assertTrue(pi.length() > 30, "expected ~40 digits, got " + pi);
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
    void degreeModeStaysExactBecauseTheFactorIsAFractionOfPi() throws Exception {
        // What AngleConversion produces for sin(30) and arcsin(1/2) in degree mode, checked against the
        // real engine. This is the claim the whole approach rests on: multiplying by a rounded
        // 0.017453292519943295 instead answers 0.49999999999999994 and turns an exact calculator into
        // an approximate one — while still looking right to three decimal places on screen.
        assertEquals("1/2", eval("sin(30*pi/180)"));
        assertEquals("30", eval("arcsin(1/2)/(pi/180)"));
        assertEquals("1", eval("tan(45*pi/180)"));
    }

    @Test
    void theModesDriveTheRealEngineFromEndToEnd() {
        // The join: a mode set on the machine, converted by AngleConversion, answered by Symja. Both
        // halves are tested separately against stubs, and neither can show that they compose.
        Machine m = new Machine(Evaluator.numericThen((input, modes) -> {
            try {
                return engine.eval(input);
            } catch (CasException e) {
                throw new IllegalStateException(e);
            }
        }));

        m.apply(new Op.SetModes(Modes.DEFAULTS.withAngle(Modes.Angle.DEGREES)));
        m.apply(new Op.Push(Parser.parse("sin(30)")));
        assertEquals("1/2", Formatter.format(m.state().at(1)), "degrees, and still exact");

        // Fraction mode off turns that same answer into a decimal, without a second evaluation.
        m.apply(new Op.SetModes(m.modes().withFractions(false)));
        m.apply(new Op.Push(Parser.parse("sin(30)")));
        assertEquals("0.5", Formatter.format(m.state().at(1)));

        // And back to radians, where sin(30) is nothing memorable at all.
        m.apply(new Op.SetModes(m.modes().withAngle(Modes.Angle.RADIANS).withFractions(true)));
        m.apply(new Op.Push(Parser.parse("sin(30)")));
        assertEquals("sin(30)", Formatter.format(m.state().at(1)));
    }

    @Test
    void onePartOfAnAnswerCanBeRewrittenInPlace() throws Exception {
        // The whole point of addressing subterms: factor the 1 - x^2 inside an answer and get the
        // answer back with only that part changed. Nothing about this is expressible by retyping.
        Expr answer = Parser.parse("sqrt(1 - x^2) + arcsin(x)");
        java.util.List<Integer> insideTheRadical = java.util.List.of(0, 0);

        Expr part = ExprPath.at(answer, insideTheRadical);
        assertEquals("1 - x^2", Formatter.format(part));

        Expr factored = engine.eval(com.calcula.expr.Exprs.call("Factor", part));
        Expr rebuilt = ExprPath.replace(answer, insideTheRadical, factored);

        assertEquals("sqrt((1 - x)*(1 + x)) + arcsin(x)", Formatter.format(rebuilt));
        assertEquals("arcsin(x)", Formatter.format(ExprPath.at(rebuilt, java.util.List.of(1))), "the rest is intact");
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
        Expr derivative = engine.eval(Parser.parse("deriv(f(x), x)"));
        String again = Formatter.format(engine.eval(derivative));
        assertEquals(Formatter.format(derivative), again, "a re-evaluated derivative must be stable");
    }
}
