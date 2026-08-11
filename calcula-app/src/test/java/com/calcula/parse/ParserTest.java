package com.calcula.parse;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserTest {

    private static Expr p(String s) {
        return Parser.parse(s);
    }

    @Test
    void numbersAreExactUnlessWrittenOtherwise() {
        assertEquals(Exprs.of(42), p("42"));
        assertInstanceOf(Expr.Flt.class, p("1.5"), "a decimal point means inexact");
        assertInstanceOf(Expr.Flt.class, p("1e3"), "an exponent means inexact");
        assertInstanceOf(Expr.Int.class, p("99999999999999999999999"), "big integers stay exact");
    }

    @Test
    void functionNamesNormaliseToEngineHeads() {
        assertEquals(Exprs.call("Sin", Exprs.sym("x")), p("sin(x)"));
        assertEquals(Exprs.call("ArcTan", Exprs.sym("x")), p("arctan(x)"));
        assertEquals(Exprs.call("Log", Exprs.sym("x")), p("ln(x)"));
        assertEquals(p("sin(x)"), p("Sin(x)"), "the stored spelling is accepted as typed too");
    }

    @Test
    void anUnknownFunctionPassesThroughVerbatim() {
        // Totality again: a head the engine knows and we have never modelled must stay callable.
        assertEquals(Exprs.call("Zeta", Exprs.of(2)), p("Zeta(2)"));
    }

    @Test
    void piIsAConstantButIAndEAreLeftAsVariables() {
        assertEquals(Exprs.PI, p("pi"));
        // An index `i` is far more common in practice than the imaginary unit; claiming it would make
        // sum(i, ...) mean something surprising.
        assertEquals(Exprs.sym("i"), p("i"));
        assertEquals(Exprs.sym("e"), p("e"));
        assertEquals(Exprs.I, p("I"));
        assertEquals(Exprs.E, p("E"));
    }

    @Test
    void additionAndMultiplicationAreLeftAssociative() {
        assertEquals(Exprs.call("Subtract", Exprs.call("Subtract", Exprs.of(1), Exprs.of(2)), Exprs.of(3)), p("1-2-3"));
        assertEquals(Exprs.call("Divide", Exprs.call("Divide", Exprs.of(8), Exprs.of(4)), Exprs.of(2)), p("8/4/2"));
    }

    @Test
    void powerIsRightAssociative() {
        // 2^3^2 is 2^9 = 512, not 64.
        assertEquals(Exprs.call("Power", Exprs.of(2), Exprs.call("Power", Exprs.of(3), Exprs.of(2))), p("2^3^2"));
    }

    @Test
    void unaryMinusBindsLooserThanPower() {
        // -2^2 is −4 everywhere in mathematics, and the parser must agree.
        assertEquals(Exprs.call("Minus", Exprs.call("Power", Exprs.sym("x"), Exprs.of(2))), p("-x^2"));
    }

    @Test
    void aNegativeExponentParses() {
        assertEquals(Exprs.call("Power", Exprs.of(2), Exprs.of(-3)), p("2^-3"));
    }

    @Test
    void aSignedLiteralFoldsIntoTheNumberRatherThanWrappingIt() {
        assertEquals(Exprs.of(-3), p("-3"));
        assertEquals(Exprs.sym("x"), p("+x"));
    }

    @Test
    void precedenceRespectsParentheses() {
        assertEquals(Exprs.call("Times", Exprs.call("Plus", Exprs.of(1), Exprs.of(2)), Exprs.of(3)), p("(1+2)*3"));
        assertEquals(Exprs.call("Plus", Exprs.of(1), Exprs.call("Times", Exprs.of(2), Exprs.of(3))), p("1+2*3"));
    }

    @Test
    void listsAndMatrices() {
        assertEquals(Exprs.list(Exprs.of(1), Exprs.of(2)), p("[1, 2]"));
        assertTrue(Exprs.isMatrix(p("[[1,2],[3,4]]")));
        assertEquals(Exprs.list(), p("[]"), "an empty list is legal");
    }

    @Test
    void relationsAndFactorial() {
        assertEquals(Exprs.call("Equal", Exprs.sym("x"), Exprs.of(1)), p("x = 1"));
        assertEquals(p("x = 1"), p("x == 1"));
        assertEquals(Exprs.call("LessEqual", Exprs.sym("x"), Exprs.of(1)), p("x <= 1"));
        assertEquals(Exprs.call("Factorial", Exprs.of(5)), p("5!"));
    }

    @Test
    void functionsTakeSeveralArguments() {
        assertEquals(Exprs.call("D", Exprs.call("Power", Exprs.sym("x"), Exprs.of(2)), Exprs.sym("x")), p("d(x^2, x)"));
        assertEquals(Exprs.call("Max", Exprs.of(1), Exprs.of(2), Exprs.of(3)), p("max(1,2,3)"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "1 +", "(1", "1)", "[1, 2", "sin(", "2x", "1 @ 2", "f(1,)"})
    void malformedInputIsRejectedWithAPosition(String bad) {
        // `2x` is in this list on purpose: implicit multiplication is refused rather than guessed at,
        // because it makes `f (x)` ambiguous with a function call.
        ParseException e = assertThrows(ParseException.class, () -> Parser.parse(bad));
        assertTrue(e.position() >= 0, "every error should carry an offset to point a caret at");
    }

    @Test
    void chainedComparisonsAreRefusedRatherThanGuessed() {
        // a < b < c means different things in different languages; refuse it.
        assertThrows(ParseException.class, () -> Parser.parse("1 < 2 < 3"));
    }
}
