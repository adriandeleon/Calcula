package com.calcula.cas.symja;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import com.calcula.parse.Formatter;
import com.calcula.parse.Functions;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What the catalogue claims, against the engine that has to honour it.
 *
 * <p>{@link Functions} is the only place in the application that says what the algebra can do, and it
 * is a plain table — nothing about writing a row makes the thing in it work. The failure mode is
 * specific and silent: an unknown head passes through untouched, so a catalogued name the engine does
 * not implement is offered, picked, and comes back exactly as it was typed. That reads as the
 * calculator ignoring you.
 *
 * <p>Symja knowing a symbol is not the test. {@code Fit} is a real Symja symbol that answers with
 * itself; {@code FindFit} is the one that works. Only running it tells them apart, which is why these
 * are concrete calls with concrete answers rather than a loop over the table asking whether each name
 * exists.
 */
class CatalogueTest {

    private static CasEngine engine;

    @BeforeAll
    static void start() {
        engine = new SymjaEngine();
    }

    private static String eval(String source) throws CasException {
        return Formatter.format(engine.eval(Parser.parse(source)));
    }

    /** Catalogued, and answering. */
    private static void answers(String source, String expected) throws CasException {
        assertNotNull(Functions.find(head(source)), head(source) + " is asserted here but not catalogued");
        assertEquals(expected, eval(source), source);
    }

    private static String head(String source) {
        int paren = source.indexOf('(');
        return paren < 0 ? source : source.substring(0, paren);
    }

    @Test
    void statistics() throws Exception {
        answers("Mean([1, 2, 3, 4])", "5/2");
        answers("Median([1, 2, 3, 4])", "5/2");
        answers("Variance([1, 2, 3, 4])", "5/3");
        answers("StandardDeviation([1, 2, 3, 4])", "(5/3)^(1/2)");
        answers("Quartiles([1, 2, 3, 4, 5])", "[7/4, 3, 17/4]");
        answers("Total([1, 2, 3])", "6");
        answers("Covariance([1, 2, 3], [2, 4, 7])", "5/2");
        // Least squares, and the reason Fit is NOT in the table: it comes back unevaluated.
        answers("FindFit([[1, 1], [2, 4], [3, 9]], a*x^2, [a], x)", "[a -> 1.0]");
        assertEquals(
                "Fit([[1, 1], [2, 4], [3, 9]], [1, x], x)",
                eval("Fit([[1, 1], [2, 4], [3, 9]], [1, x], x)"),
                "if this ever starts answering, Fit has earned a row");
    }

    @Test
    void lists() throws Exception {
        answers("Range(1, 5)", "[1, 2, 3, 4, 5]");
        answers("Table(i^2, [i, 1, 4])", "[1, 4, 9, 16]");
        answers("Map(f, [1, 2, 3])", "[f(1), f(2), f(3)]");
        answers("Apply(Plus, [1, 2, 3])", "6");
        answers("Fold(Plus, 0, [1, 2, 3])", "6");
        answers("Select([1, 2, 3, 4], EvenQ)", "[2, 4]");
        answers("Sort([3, 1, 2])", "[1, 2, 3]");
        answers("Reverse([1, 2, 3])", "[3, 2, 1]");
        answers("Length([1, 2, 3])", "3");
        answers("Part([1, 2, 3], 2)", "2");
        answers("Take([1, 2, 3, 4], 2)", "[1, 2]");
        answers("Flatten([[1, 2], [3]])", "[1, 2, 3]");
        answers("Union([1, 2], [2, 3])", "[1, 2, 3]");
        answers("Norm([3, 4])", "5");
        answers("Cross([1, 0, 0], [0, 1, 0])", "[0, 0, 1]");
    }

    @Test
    void numberTheory() throws Exception {
        answers("NextPrime(100)", "101");
        answers("PrimePi(100)", "25");
        answers("EulerPhi(10)", "4");
        answers("MoebiusMu(30)", "-1");
        answers("DivisorSigma(1, 12)", "28");
        answers("PowerMod(2, 10, 7)", "2");
        answers("QuotientRemainder(17, 5)", "[3, 2]");
        answers("ContinuedFraction(415/93)", "[4, 2, 6, 7]");
        answers("BernoulliB(4)", "-1/30");
        answers("HarmonicNumber(5)", "137/60");
        answers("LucasL(10)", "123");
        answers("CatalanNumber(5)", "42");
        answers("IntegerDigits(255, 2)", "[1, 1, 1, 1, 1, 1, 1, 1]");
        answers("FromDigits([1, 1, 1, 1, 1, 1, 1, 1], 2)", "255");
    }

    @Test
    void specialFunctionsAreExactWhereTheyCanBeAndNumericWhenAsked() throws Exception {
        answers("Gamma(5)", "24");
        assertEquals("pi^(1/2)", eval("Gamma(1/2)"));
        answers("Beta(2, 3)", "1/12");
        answers("Zeta(2)", "pi^2/6");
        // No closed form, so it stays itself — which is correct and looks like nothing happening.
        // Hence the summaries saying to wrap it in N.
        assertEquals("Erf(1)", eval("Erf(1)"));
        assertEquals("0.8427007929497149", eval("N(Erf(1))"));
        assertEquals("0.7651976865579572", eval("N(BesselJ(0, 1))"));
    }

    @Test
    void numeric() throws Exception {
        answers("FindRoot(cos(x) - x, [x, 1])", "[x -> 0.7390851332151607]");
        answers("FindMinimum(x^2 - 4*x, [x, 0])", "[-4.0, [x -> 2.0]]");
        answers("NMaximize(-x^2 + 4, x)", "[4.0, [x -> 0.0]]");
        answers("NIntegrate(sin(x), [x, 0, pi])", "2.000000000000001");
        answers("Rationalize(0.75)", "3/4");
        answers("IntegerPart(2.7)", "2");
        // An interval carries its bounds through arithmetic, which is most of what makes it worth
        // listing at all.
        answers("Interval([1, 2])", "Interval([1, 2])");
        assertEquals("Interval([2, 3])", eval("Interval([1, 2]) + 1"));
    }

    @Test
    void algebraAndComplex() throws Exception {
        answers("FullSimplify(sin(x)^2 + cos(x)^2)", "1");
        answers("PolynomialQuotient(x^2 - 1, x - 1, x)", "1 + x");
        answers("PolynomialRemainder(x^2, x - 1, x)", "1");
        answers("ComplexExpand((1 + I)^2)", "I*2");
    }

    @Test
    void unitsAreNotCatalogued() {
        // Symja has Quantity and UnitConvert, and neither can be reached: they take a string, and
        // this notation has no string literal. The lexer refuses the quote before the engine is ever
        // asked. Recorded here because the symbols existing is exactly what makes units look like a
        // table edit away, and they are not — see the units issue.
        assertEquals(null, Functions.find("Quantity"));
        assertEquals(null, Functions.find("UnitConvert"));
        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class, () -> Parser.parse("UnitConvert(Quantity(3, \"m\"), \"ft\")"));
    }
}
