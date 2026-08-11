package com.calcula.parse;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatterTest {

    private static String f(String source) {
        return Formatter.format(Parser.parse(source));
    }

    @Test
    void redundantParenthesesAreDropped() {
        // Tight `*`, spaced `+`: the spacing mirrors precedence, as in most CAS output.
        assertEquals("1 + 2*3", f("1 + (2 * 3)"));
        assertEquals("1 + 2 + 3", f("(1 + 2) + 3"));
        assertEquals("x^2", f("(x)^(2)"));
    }

    @Test
    void necessaryParenthesesAreKept() {
        assertEquals("(1 + 2)*3", f("(1 + 2) * 3"));
        // The one that matters: a - (b - c) is not a - b - c.
        assertEquals("1 - (2 - 3)", f("1 - (2 - 3)"));
        assertEquals("8 / (4 / 2)", f("8 / (4 / 2)"));
    }

    @Test
    void associativitySidesAreHandledIndependently() {
        // Power is right-associative, so it is the LEFT child that needs bracketing.
        assertEquals("(2^3)^2", f("(2^3)^2"));
        assertEquals("2^3^2", f("2^3^2"));
    }

    @Test
    void negativeLiteralsAreBracketedWhereTheSignWouldBeCapturedByATighterOperator() {
        // -3! would re-parse as -(3!) — a wrong answer created purely by formatting.
        assertEquals("(-3)!", Formatter.format(Exprs.call("Factorial", Exprs.of(-3))));
        assertEquals("x^(-3)", Formatter.format(Exprs.call("Power", Exprs.sym("x"), Exprs.of(-3))));
        assertEquals("1 - -3", Formatter.format(Exprs.call("Subtract", Exprs.of(1), Exprs.of(-3))));
    }

    @Test
    void headsPrintWithTheirFriendlySpelling() {
        assertEquals("sin(x)", f("Sin(x)"));
        assertEquals("ln(x)", f("log(x)"), "Log prints back as ln, the spelling registered first");
        assertEquals("arctan(x)", f("atan(x)"));
        assertEquals("Zeta(2)", f("Zeta(2)"), "an unknown head keeps its own spelling");
    }

    @Test
    void listsUseBracketNotation() {
        assertEquals("[1, 2, 3]", f("[1,2,3]"));
        assertEquals("[[1, 2], [3, 4]]", f("[[1,2],[3,4]]"));
    }

    @Test
    void aRationalPrintsAsAQuotient() {
        assertEquals("1/2", Formatter.format(Exprs.rat(1, 2)));
        assertEquals("(-1/2)!", Formatter.format(Exprs.call("Factorial", Exprs.rat(-1, 2))));
    }

    // ---- engine canonical forms -------------------------------------------------------------
    // The engine has no subtraction, no division and no unary minus, and its Plus/Times are n-ary.
    // These are the shapes every result actually arrives in.

    private static Expr times(Expr... args) {
        return Exprs.call("Times", args);
    }

    private static Expr reciprocal(Expr e) {
        return Exprs.call("Power", e, Exprs.of(-1));
    }

    @Test
    void aNegatedFactorBecomesAMinusSign() {
        assertEquals("-x", Formatter.format(times(Exprs.of(-1), Exprs.sym("x"))));
        assertEquals(
                "a - b", Formatter.format(Exprs.call("Plus", Exprs.sym("a"), times(Exprs.of(-1), Exprs.sym("b")))));
    }

    @Test
    void aNegativeRationalCoefficientAlsoBecomesAMinusSign() {
        // The engine returns -ln(u)/6 as Times(-1/6, Log(u)). Matching only a coefficient of exactly
        // -1 leaves this printing as `a + -ln(u)/6`, which reads like an algebra bug rather than a
        // formatting one.
        Expr term = times(Exprs.rat(-1, 6), Exprs.call("Log", Exprs.sym("u")));
        assertEquals("a - ln(u)/6", Formatter.format(Exprs.call("Plus", Exprs.sym("a"), term)));
    }

    @Test
    void reciprocalFactorsBecomeADenominator() {
        assertEquals("1/x", Formatter.format(reciprocal(Exprs.sym("x"))));
        assertEquals("a/b", Formatter.format(times(Exprs.sym("a"), reciprocal(Exprs.sym("b")))));
        // Several reciprocals must be bracketed together: a/(b*c) is not a/b*c.
        assertEquals(
                "a/(b*c)",
                Formatter.format(times(Exprs.sym("a"), reciprocal(Exprs.sym("b")), reciprocal(Exprs.sym("c")))));
    }

    @Test
    void aRationalCoefficientMovesIntoTheDenominator() {
        // Sum(1/n^2, ...) comes back as Times(1/6, Power(Pi, 2)); nobody writes that as 1/6*pi^2.
        Expr e = times(Exprs.rat(1, 6), Exprs.call("Power", Exprs.PI, Exprs.of(2)));
        assertEquals("pi^2/6", Formatter.format(e));
    }

    @Test
    void anUnmodelledEngineValueShowsItsOwnPrintedFormRatherThanTheWrapper() {
        Expr wrapped = Exprs.call("$Engine", Exprs.sym("-5+I*10"));
        assertEquals("-5+I*10", Formatter.format(wrapped));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "1 + 2 * 3",
                "(1 + 2) * 3",
                "1 - (2 - 3)",
                "2^3^2",
                "(2^3)^2",
                "-x^2",
                "2^-3",
                "sin(x) + cos(y)",
                "deriv(x^2, x)",
                "[1, 2, [3, 4]]",
                "x = 1",
                "x <= 1",
                "5!",
                "max(1, 2, 3)",
                "8 / (4 / 2)",
                "-(x + 1)",
                "1 / (x + 1)",
                "Zeta(2) * pi"
            })
    void formattingIsStableAndLosslessOverAnythingTheParserCanBuild(String source) {
        // The real invariant: parse(format(parse(s))) == parse(s). Stated over parser output rather
        // than arbitrary trees, because numeric literals legitimately normalise on the way back.
        Expr once = Parser.parse(source);
        Expr twice = Parser.parse(Formatter.format(once));
        assertEquals(once, twice, "round trip changed the tree for: " + source);
        assertEquals(Formatter.format(once), Formatter.format(twice), "formatting is not idempotent");
    }
}
