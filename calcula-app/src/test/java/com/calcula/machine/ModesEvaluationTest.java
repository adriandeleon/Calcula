package com.calcula.machine;

import java.util.List;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import com.calcula.machine.Modes.Angle;
import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the mode line is not decoration.
 *
 * <p>Each mode is checked for the thing it claims to do, because a mode that displays in the mode line
 * and changes nothing is worse than no mode at all — it is a wrong answer the user has been told to
 * expect.
 */
class ModesEvaluationTest {

    private static String evalNumeric(String source, Modes modes) {
        return Formatter.format(Evaluator.NUMERIC.evaluate(Parser.parse(source), modes));
    }

    // ------------------------------------------------------------------ precision

    @Test
    void precisionSetsTheWorkingDigitsForInexactArithmetic() {
        Modes three = Modes.DEFAULTS.withPrecision(3);
        assertEquals("0.333", evalNumeric("1.0/3", three));
        assertEquals("0.333333333333", evalNumeric("1.0/3", Modes.DEFAULTS));
    }

    @Test
    void precisionDoesNotDamageExactArithmetic() {
        // Three digits of working precision must not round 1/3; an exact rational has no rounding to do.
        assertEquals("1/3", evalNumeric("1/3", Modes.DEFAULTS.withPrecision(3)));
    }

    // ------------------------------------------------------------------ fractions

    @Test
    void turningFractionsOffGivesADecimal() {
        Evaluator chain = Evaluator.numericThen(Evaluator.IDENTITY);
        Expr result = chain.evaluate(Parser.parse("1/3"), Modes.DEFAULTS.withFractions(false));
        assertEquals("0.333333333333", Formatter.format(result));
    }

    @Test
    void aFractionBuriedInsideAnAnswerIsConvertedToo() {
        // The whole tree, not just a bare top-level number: 1/3 + x is where someone who turned the
        // mode off would still be looking at a fraction.
        Evaluator chain = Evaluator.numericThen(Evaluator.IDENTITY);
        Expr result = chain.evaluate(Parser.parse("1/3 + x"), Modes.DEFAULTS.withFractions(false));
        assertFalse(Formatter.format(result).contains("1/3"), Formatter.format(result));
        assertTrue(Formatter.format(result).contains("0.333"), Formatter.format(result));
    }

    @Test
    void fractionsOnLeavesTheRationalAlone() {
        Evaluator chain = Evaluator.numericThen(Evaluator.IDENTITY);
        assertEquals("1/3", Formatter.format(chain.evaluate(Parser.parse("1/3"), Modes.DEFAULTS)));
    }

    // ------------------------------------------------------------------ symbolic

    @Test
    void symbolicOffAsksTheEngineForANumber() {
        // A CAS answers 2^(1/2) with sqrt(2) — still exact — and only produces a decimal when asked.
        String[] asked = new String[1];
        Evaluator chain = Evaluator.numericThen((input, modes) -> {
            asked[0] = Formatter.format(input);
            return input instanceof com.calcula.expr.Expr.Call c && "N".equals(c.head())
                    ? Exprs.of(new java.math.BigDecimal("1.4142135624"))
                    : Exprs.call("Sqrt", Exprs.of(2));
        });
        Expr result = chain.evaluate(Parser.parse("2^(1/2)"), Modes.DEFAULTS.withSymbolic(false));
        assertTrue(asked[0].startsWith("N("), "expected an N() request, got " + asked[0]);
        assertEquals("1.4142135624", Formatter.format(result));
    }

    @Test
    void symbolicOffStopsAsSoonAsThereIsANumberToShow() {
        // An answer that is already a number needs no second round trip, and a CAS call is the
        // expensive thing here.
        int[] calls = new int[1];
        Evaluator chain = Evaluator.numericThen((input, modes) -> {
            calls[0]++;
            return Exprs.of(new java.math.BigDecimal("2.5"));
        });
        chain.evaluate(Parser.parse("x"), Modes.DEFAULTS.withSymbolic(false));
        assertEquals(1, calls[0]);
    }

    @Test
    void symbolicOffFallsBackToTheExactFormWhenTheEngineCannotNumericiseIt() {
        // No engine at all is the same shape: the request comes back untouched. Leaving N(x, 12) on
        // the stack as an "answer" would be worse than the exact form the mode was turned off to avoid.
        Evaluator chain = Evaluator.numericThen(Evaluator.IDENTITY);
        Expr result = chain.evaluate(Parser.parse("x + 1"), Modes.DEFAULTS.withSymbolic(false));
        assertEquals("x + 1", Formatter.format(result));
    }

    @Test
    void symbolicOnNeverAsksForANumber() {
        Evaluator chain = Evaluator.numericThen((input, modes) -> {
            assertFalse(Formatter.format(input).startsWith("N("), "symbolic mode should not numericise");
            return input;
        });
        chain.evaluate(Parser.parse("2^(1/2)"), Modes.DEFAULTS);
    }

    // ------------------------------------------------------------------ angle

    @Test
    void theAngleUnitIsRewrittenIntoWhatTheEngineIsAsked() {
        String[] asked = new String[1];
        Evaluator chain = Evaluator.numericThen((input, modes) -> {
            asked[0] = Formatter.format(input);
            return input;
        });
        chain.evaluate(Parser.parse("sin(30)"), Modes.DEFAULTS.withAngle(Angle.DEGREES));
        assertEquals("sin(30*pi/180)", asked[0]);
    }

    // ------------------------------------------------------------------ as operations

    @Test
    void aModeChangeIsUndoable() {
        Machine m = new Machine(Evaluator.IDENTITY);
        m.apply(new Op.SetModes(Modes.DEFAULTS.withAngle(Angle.DEGREES)));
        assertEquals(Angle.DEGREES, m.modes().angle());

        assertTrue(m.undo());
        assertEquals(Angle.RADIANS, m.modes().angle(), "undo should take the mode back with the answers");
    }

    @Test
    void settingTheSameModesIsNotAnOperation() {
        // Otherwise pressing "degrees" twice needs two undos to get back, and the second one appears
        // to do nothing at all.
        Machine m = new Machine(Evaluator.IDENTITY);
        m.apply(new Op.SetModes(Modes.DEFAULTS));
        assertFalse(m.canUndo());
    }

    @Test
    void theMachinePassesItsOwnModesToTheEvaluator() {
        // Not the modes captured when the evaluator was built: they live in the state, so they move
        // with undo, and an evaluator holding its own copy would compute to a stale precision.
        Modes[] seen = new Modes[1];
        Machine m = new Machine((input, modes) -> {
            seen[0] = modes;
            return input;
        });
        m.apply(new Op.SetModes(Modes.DEFAULTS.withPrecision(40)));
        m.apply(new Op.Push(Exprs.sym("x")));
        assertEquals(40, seen[0].precision());
    }

    @Test
    void modesSurviveOperationsThatOnlyTouchTheStack() {
        Machine m = new Machine(Evaluator.IDENTITY);
        m.apply(new Op.SetModes(Modes.DEFAULTS.withAngle(Angle.GRADIANS)));
        m.applyAll(List.of(new Op.Push(Exprs.of(1)), new Op.Push(Exprs.of(2)), new Op.Swap()));
        assertEquals(Angle.GRADIANS, m.modes().angle());
    }

    @Test
    void theModeLineSpellsOutEveryMode() {
        assertEquals("rad  prec 12  symb  frac", Modes.DEFAULTS.describe());
        assertEquals(
                "deg  prec 20",
                Modes.DEFAULTS
                        .withAngle(Angle.DEGREES)
                        .withPrecision(20)
                        .withSymbolic(false)
                        .withFractions(false)
                        .describe());
    }

    @Test
    void theMathContextTracksThePrecision() {
        assertSame(20, Modes.DEFAULTS.withPrecision(20).mathContext().getPrecision());
    }
}
