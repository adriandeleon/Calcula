package com.calcula.machine;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The five levels, each tested for the thing it claims to do.
 *
 * <p>The engine is a stand-in that answers only what it is asked here, which is the point: what is
 * being tested is <em>which</em> question gets asked, not what Symja does with it.
 */
class SimplificationTest {

    /** Answers Simplify and FullSimplify with a marker, and everything else with its input. */
    private static final Evaluator ENGINE = (input, modes) -> {
        if (input instanceof Expr.Call c && c.arity() == 1) {
            if (c.head().equals("Simplify")) {
                return Exprs.sym("simplified");
            }
            if (c.head().equals("FullSimplify")) {
                return Exprs.sym("fully");
            }
        }
        return input;
    };

    private static String evaluated(String source, Simplification level) {
        Modes modes = Modes.DEFAULTS.withSimplification(level);
        return Formatter.format(Evaluator.numericThen(ENGINE).evaluate(Parser.parse(source), modes));
    }

    @Test
    void noneDoesNotEvaluateAtAll() {
        // The shape is the point. Nothing folds, not even the arithmetic.
        assertEquals("2 + 3", evaluated("2 + 3", Simplification.NONE));
        assertEquals("x + x", evaluated("x + x", Simplification.NONE));
    }

    @Test
    void numericFoldsTheArithmeticAndNeverAsksTheEngine() {
        assertEquals("5", evaluated("2 + 3", Simplification.NUMERIC));
        assertEquals("x + 1", evaluated("x + 1", Simplification.NUMERIC), "the engine was not consulted");
    }

    @Test
    void theDefaultIsWhatItAlwaysWas() {
        assertEquals("5", evaluated("2 + 3", Simplification.DEFAULT));
        assertEquals("x + 1", evaluated("x + 1", Simplification.DEFAULT));
    }

    @Test
    void algebraicAsksTheEngineToSimplify() {
        assertEquals("simplified", evaluated("x + 1", Simplification.ALGEBRAIC));
    }

    @Test
    void extendedAsksItToTryHarder() {
        assertEquals("fully", evaluated("x + 1", Simplification.EXTENDED));
    }

    @Test
    void anEngineThatCannotSimplifyLeavesTheAnswerAlone() {
        // Never Simplify(x) on the stack as though it were an answer: an engine that returns the call
        // unevaluated — or is not there at all — has to be detected and discarded.
        Evaluator unhelpful = (input, modes) -> input;
        Modes modes = Modes.DEFAULTS.withSimplification(Simplification.ALGEBRAIC);
        assertEquals(
                "x + 1", Formatter.format(Evaluator.numericThen(unhelpful).evaluate(Parser.parse("x + 1"), modes)));
    }

    @Test
    void theModeLineSaysWhichLevelExceptTheDefault() {
        assertEquals(Modes.DEFAULTS.describe(), described(Simplification.DEFAULT), "the default announces nothing");
        // Named rather than abbreviated: it is the one level that changes what an answer IS, and
        // somebody who left it on has to be able to see why nothing is computing.
        assertTrue(described(Simplification.NONE).contains("no eval"));
        assertTrue(described(Simplification.ALGEBRAIC).contains("alg"));
        assertTrue(described(Simplification.EXTENDED).contains("ext"));
    }

    private static String described(Simplification level) {
        return Modes.DEFAULTS.withSimplification(level).describe();
    }
}
