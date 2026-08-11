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
        assertEquals("1 + 2 * 3", f("1 + (2 * 3)"));
        assertEquals("1 + 2 + 3", f("(1 + 2) + 3"));
        assertEquals("x^2", f("(x)^(2)"));
    }

    @Test
    void necessaryParenthesesAreKept() {
        assertEquals("(1 + 2) * 3", f("(1 + 2) * 3"));
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
                "d(x^2, x)",
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
