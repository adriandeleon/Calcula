package com.calcula.machine;

import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Numbers in a ring, typed and worked out with no engine anywhere near it. */
class ModuloEvaluationTest {

    private static String evaluated(String source) {
        return Formatter.format(Evaluator.NUMERIC.evaluate(Parser.parse(source), Modes.DEFAULTS));
    }

    @Test
    void arithmeticStaysInTheRing() {
        assertEquals("2 mod 7", evaluated("(5 mod 7) + (4 mod 7)"));
        assertEquals("6 mod 7", evaluated("(5 mod 7) * (4 mod 7)"));
        assertEquals("5 mod 7", evaluated("(3 mod 7) / (2 mod 7)"));
    }

    @Test
    void anOrdinaryNumberJoinsTheRing() {
        assertEquals("2 mod 7", evaluated("(5 mod 7) + 4"));
        assertEquals("6 mod 7", evaluated("4 * (5 mod 7)"));
    }

    @Test
    void aPowerIsComputedInTheRing() {
        // Not by raising and then reducing: the other way round this is a number with 845,000 digits.
        assertEquals("9 mod 13", evaluated("(7 mod 13)^1000000"));
    }

    @Test
    void theFormIsWrittenBackTheWayItWasTyped() {
        assertEquals("5 mod 7", Formatter.format(Parser.parse("5 mod 7")));
        assertEquals("x mod 7", Formatter.format(Parser.parse("x mod 7")));
    }

    @Test
    void theModFunctionIsStillTheModFunction() {
        // A form remembers its ring; the function takes a remainder once and hands back an integer.
        // Asserted structurally rather than by value, because Mod is the engine's and this evaluator
        // has none — which is itself the distinction: a form folds here, a function does not.
        assertEquals("Modulo", head("5 mod 7"));
        assertEquals("Mod", head("mod(5, 7)"));
        assertEquals("mod(5, 7) + mod(4, 7)", evaluated("mod(5, 7) + mod(4, 7)"), "no ring, so nothing folds");
    }

    private static String head(String source) {
        return Parser.parse(source) instanceof com.calcula.expr.Expr.Call c ? c.head() : "";
    }

    @Test
    void somethingImpossibleLeavesTheExpressionStanding() {
        // No inverse, different rings, a symbol in there: all of them stand rather than shouting from
        // inside a fold.
        assertEquals("(1 mod 4)/(2 mod 4)", evaluated("(1 mod 4) / (2 mod 4)"));
        assertEquals("(1 mod 7) + (1 mod 5)", evaluated("(1 mod 7) + (1 mod 5)"));
        assertEquals("(1 mod 7) + x", evaluated("(1 mod 7) + x"));
    }

    @Test
    void ordinaryArithmeticIsUntouched() {
        assertEquals("5", evaluated("2 + 3"));
        assertEquals("5/6", evaluated("1/2 + 1/3"));
    }
}
