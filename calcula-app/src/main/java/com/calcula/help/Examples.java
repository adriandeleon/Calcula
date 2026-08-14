package com.calcula.help;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The worked examples: what this calculator can do, in a form you can press.
 *
 * <p>Curated rather than generated. A list built from the function catalogue would be a second copy of
 * the function sheet with brackets filled in — the value here is in the choosing: an example is worth
 * including when its <em>result</em> teaches something, and most functions do not have one of those.
 *
 * <p><b>Every line here is checked against the real engine.</b> {@code ExamplesTest} parses each one and
 * evaluates it, because an example that does not work is worse than no example: it is the first thing a
 * new user tries, and it teaches them that the application is broken.
 */
public final class Examples {

    private static final List<Example> ALL = new ArrayList<>();

    static {
        group(
                "Start here",
                ex("Exact arithmetic", "1/3 + 1/6", "A third and a sixth is a half — not 0.5, and not 0.49999999."),
                ex("Big integers, exactly", "2^200", "No overflow and no scientific notation: every digit is there."),
                ex("A number when you want one", "N(pi, 40)", "Exact by default; N is how you ask for digits."),
                ex("Roots stay roots", "sqrt(8)", "Simplified to 2*sqrt(2) rather than turned into 2.828…"));

        group(
                "Algebra",
                ex("Multiply out", "expand((x + 1)^5)", "Binomial coefficients, without looking them up."),
                ex("Put it back", "factor(x^3 - 6*x^2 + 11*x - 6)", "The three roots are readable straight off."),
                ex("Solve for x", "solve(x^2 - 5*x + 6 == 0, x)", "Two exact roots, as a list."),
                ex("A quadratic with no nice roots", "solve(x^2 + x - 1 == 0, x)", "Surds, not decimals."),
                ex("Partial fractions", "Apart(1/(x^2 - 1))", "The form you need before integrating it."),
                ex("Over one denominator", "Together(1/x + 1/(x + 1))", "The reverse of the one above."),
                ex("Simplify a trig identity", "simplify(sin(x)^2 + cos(x)^2)", "One."),
                ex("Group by powers", "Collect(x*a + x^2*b + x*c, x)", "Terms gathered by the power of x."));

        group(
                "Calculus",
                ex("Differentiate", "deriv(x^3*sin(x), x)", "The product rule, applied for you."),
                ex("Integrate", "integrate(x*exp(x), x)", "Integration by parts."),
                ex("A definite integral", "integrate(sin(x)^2, [x, 0, pi])", "Exactly pi/2."),
                ex(
                        "A limit that needs care",
                        "limit(sin(x)/x, x -> 0)",
                        "One — the limit L'Hopital is usually shown for."),
                ex("Sum a series", "sum(1/n^2, [n, 1, infinity])", "pi^2/6. The Basel problem, in one line."),
                ex("Taylor series", "Series(exp(x), [x, 0, 6])", "The expansion about zero, to sixth order."),
                ex("Second derivative", "deriv(deriv(x^4 - 3*x^2, x), x)", "Differentiate the derivative."));

        group(
                "Matrices",
                ex("A matrix on the stack", "[[1, 2], [3, 4]]", "Typeset with brackets, not printed as a list."),
                ex("Determinant", "Det([[1, 2], [3, 4]])", "Minus two."),
                ex("Inverse", "Inverse([[1, 2], [3, 4]])", "Exact fractions, not rounded ones."),
                ex(
                        "Matrix product",
                        "dot([[1, 2], [3, 4]], [[0, 1], [1, 0]])",
                        "dot multiplies; a plain * is elementwise."),
                ex("Eigenvalues", "Eigenvalues([[2, 1], [1, 2]])", "Three and one."),
                ex("Solve a linear system", "LinearSolve([[2, 1], [1, 3]], [5, 10])", "Two equations, exactly."));

        group(
                "Numbers",
                ex("Prime factors", "FactorInteger(2^64 - 1)", "The factorisation, as base-and-exponent pairs."),
                ex("Is it prime?", "PrimeQ(2^31 - 1)", "True — the eighth Mersenne prime."),
                ex("The 1000th prime", "Prime(1000)", "7919."),
                ex("Greatest common divisor", "gcd(1071, 462)", "Euclid, instantly."),
                ex("A large factorial", "factorial(50)", "Every one of the sixty-five digits."),
                ex("Fibonacci", "Fibonacci(100)", "Exact, where a double would have given up long ago."));

        group(
                "Trigonometry and complex numbers",
                ex("An exact angle", "sin(pi/3)", "sqrt(3)/2 — the value, not an approximation of it."),
                ex("Euler's identity", "simplify(exp(I*pi) + 1)", "Zero."),
                ex("Complex arithmetic", "(3 + 4*I)/(1 - 2*I)", "Divided out into a + b*I form."),
                ex("Modulus", "abs(3 + 4*I)", "Five."),
                ex("Degrees, if you set them", "cos(60)", "In degrees mode this is 1/2. Mode > Degrees."));

        group(
                "Plots",
                plot("A curve", "x*sin(x)", "The stack's top, drawn. Drag to pan, scroll to zoom."),
                plot("A pole", "1/(x - 1)", "The asymptote is sampled around, not drawn through."),
                plot("Something wilder", "sin(x)*exp(-x^2/10)", "A wave packet."),
                plot("A polynomial", "x^3 - 3*x", "Two turning points, where the derivative vanishes."),
                surface(
                        "A surface",
                        "sin(x)*cos(y)",
                        "Two free variables, so it draws in three dimensions. Drag to turn it, scroll to zoom."),
                surface("A hill", "exp(-(x^2 + y^2)/3)", "The height scale stands at the left, outside the shape."));

        group(
                "Measurements",
                ex(
                        "Error carries through",
                        "(2 +/- 0.1) * (3 +/- 0.2)",
                        "6 +/- 0.5, without doing the calculus by hand. Errors combine in quadrature."),
                ex(
                        "Subtracting a measurement from itself",
                        "(2 +/- 0.1) - (2 +/- 0.1)",
                        "Not zero-error: the two readings are independent, and claiming otherwise would invent a correlation."),
                ex("Stay in the ring", "(5 mod 7) * (4 mod 7)", "Six. The arithmetic never leaves the small numbers."),
                ex("A range, carried", "(1 .. 2) * (3 .. 4)", "3 .. 8 — the bounds go through the multiplication."),
                ex(
                        "Time adds up",
                        "1@ 30\' 0\" + 0@ 45\' 0\"",
                        "Two and a quarter hours. Sixty is a fact about printing, not about the arithmetic."),
                ex(
                        "How many of one fit in the other",
                        "(24@ 0\' 0\") / (0@ 30\' 0\")",
                        "Forty-eight — a count, not a time."));

        group(
                "Units",
                ex("Unlike units add", "3 ft + 2 m", "Converted exactly: 3643/1250 of a metre, not 2.9144."),
                ex("Units multiply out", "3 m / 2 s", "A speed, with the unit worked out for you."),
                ex(
                        "Metres and seconds do not add",
                        "3 m + 2 s",
                        "It stands unevaluated rather than answering — which is the correct answer."),
                ex("Convert", "UnitConvert(100 km, mi)", "Exactly 781250/12573 miles."),
                ex(
                        "Temperature is not a factor",
                        "UnitConvert(100 degC, degF)",
                        "212 on the nose. Temperature has an offset as well as a scale, so it converts here rather than in the engine."));

        group(
                "Statistics",
                ex("The average", "Mean([2, 4, 4, 4, 5, 5, 7, 9])", "Five, exactly — build the list first with Pack."),
                ex("Spread", "StandardDeviation([2, 4, 4, 4, 5, 5, 7, 9])", "A real square root, not 2.13809."),
                ex(
                        "Fit a line",
                        "FindFit([[1, 2], [2, 4], [3, 7]], a*x + b, [a, b], x)",
                        "The fit comes back as a formula, which you can plot beside the data."));

        group(
                "Rules",
                ex(
                        "Rewrite once",
                        "ReplaceAll(x^2 + 1, x -> 3)",
                        "Ten. A rule is read rather than evaluated — x -> 3 alone would be a question about x."),
                ex(
                        "Rewrite when it applies",
                        "ReplaceAll(5, x_ :> x^2 /; x > 3)",
                        "Twenty-five. Change the 5 to a 2 and nothing happens: the condition gates the rule."));

        group(
                "Working with a result",
                ex(
                        "Take a result apart",
                        "expand((x + 1)^3)",
                        "Click any part of the answer, then Alt+Up to widen the selection — every command acts on what is selected."),
                ex(
                        "Rewrite in place",
                        "integrate(x^2 + sin(x), x)",
                        "Select just one term and apply a function to it; the rest of the expression stays where it is."),
                ex(
                        "Two on the stack",
                        "x^2 - 1",
                        "Run it, then type x + 1 and run that too — values pile up, and every operation takes its arguments from the top."));
    }

    private Examples() {}

    /** Everything, in table order. */
    public static List<Example> all() {
        return List.copyOf(ALL);
    }

    /** Those matching a query across title, text and note; everything when the query is blank. */
    public static List<Example> matching(String query) {
        if (query == null || query.isBlank()) {
            return all();
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Example> hits = new ArrayList<>();
        for (Example example : ALL) {
            if (example.haystack().contains(needle)) {
                hits.add(example);
            }
        }
        return hits;
    }

    // ---- table helpers ----------------------------------------------------------------------------

    private static void group(String heading, Example... examples) {
        for (Example e : examples) {
            ALL.add(new Example(heading, e.title(), e.source(), e.next(), e.note()));
        }
    }

    private static Example ex(String title, String source, String note) {
        return new Example("", title, source, null, note);
    }

    /**
     * An example whose point is the picture: run it, then plot what it left on the stack.
     *
     * <p>Two steps rather than one, and shown as two. Drawing the curve automatically would hide the
     * fact that plotting acts on the top of the stack — which is the thing worth learning here.
     */
    private static Example plot(String title, String source, String note) {
        return new Example("", title, source, "plot.function", note);
    }

    /** The same, for a function of two variables, which draws as a surface rather than a curve. */
    private static Example surface(String title, String source, String note) {
        return new Example("", title, source, "plot.surface", note);
    }
}
