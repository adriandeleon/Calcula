package com.calcula.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What you can actually call, with what it takes and what it does.
 *
 * <p>{@link Names} answers "does this spelling translate", which is not the same question as "what
 * exists". A user who types {@code isprime(97)} gets it back unevaluated and has no way to discover
 * that the engine spells it {@code PrimeQ} — the totality rule that makes unknown heads survive is also
 * what makes a wrong guess fail silently. This table is the answer to that: it is the only place in the
 * application that claims to know what the algebra can do.
 *
 * <p>Two kinds of entry, and the difference is visible to the user:
 *
 * <ul>
 *   <li><b>Friendly names</b> — {@code integrate}, {@code deriv} — which {@link Names} translates.
 *   <li><b>Engine names</b> — {@code PrimeQ}, {@code Fibonacci} — which pass through verbatim. They
 *       work because unknown heads survive the round trip, and they are listed because working and
 *       being findable are different things.
 * </ul>
 *
 * <p>Curated rather than generated. Symja has well over a thousand symbols, and a completion list of a
 * thousand entries is a worse answer to "what can this do" than a list of eighty.
 */
public final class Functions {

    /**
     * One callable thing.
     *
     * @param name what to type
     * @param signature how to call it, e.g. {@code integrate(f, x)}
     * @param summary one line, in the imperative
     * @param group heading for the help sheet
     */
    public record Doc(String name, String signature, String summary, String group) {}

    private static final List<Doc> ALL = new ArrayList<>();

    static {
        group(
                "Calculus",
                doc("deriv", "deriv(f, x)", "Differentiate f with respect to x"),
                doc("integrate", "integrate(f, x)", "Integrate f with respect to x"),
                doc("integrate", "integrate(f, [x, a, b])", "Definite integral from a to b"),
                doc("limit", "limit(f, x -> a)", "Limit of f as x approaches a"),
                doc("sum", "sum(f, [n, a, b])", "Sum f over n from a to b"),
                doc("product", "product(f, [n, a, b])", "Product of f over n from a to b"),
                doc("Series", "Series(f, [x, a, n])", "Series expansion of f about a to order n"),
                doc("D", "D(f, x)", "Differentiate — the engine's own name for deriv"));

        group(
                "Algebra",
                doc("solve", "solve(equation, x)", "Solve an equation for x"),
                doc("simplify", "simplify(f)", "Simplify an expression"),
                doc("expand", "expand(f)", "Multiply out products and powers"),
                doc("factor", "factor(f)", "Factor into a product"),
                doc("Together", "Together(f)", "Put over a common denominator"),
                doc("Apart", "Apart(f)", "Split into partial fractions"),
                doc("Numerator", "Numerator(f)", "The top of a fraction"),
                doc("Denominator", "Denominator(f)", "The bottom of a fraction"),
                doc("Collect", "Collect(f, x)", "Group terms by powers of x"),
                doc("Coefficient", "Coefficient(f, x, n)", "The coefficient of x^n"),
                doc("Roots", "Roots(equation, x)", "The roots of an equation"),
                doc("NSolve", "NSolve(equation, x)", "Solve numerically"));

        group(
                "Arithmetic",
                doc("sqrt", "sqrt(x)", "Square root"),
                doc("exp", "exp(x)", "e to the power x"),
                doc("ln", "ln(x)", "Natural logarithm"),
                doc("log10", "log10(x)", "Logarithm base 10"),
                doc("log2", "log2(x)", "Logarithm base 2"),
                doc("abs", "abs(x)", "Absolute value"),
                doc("sign", "sign(x)", "−1, 0 or 1"),
                doc("floor", "floor(x)", "Round down"),
                doc("ceil", "ceil(x)", "Round up"),
                doc("round", "round(x)", "Round to nearest"),
                doc("max", "max(a, b)", "The larger"),
                doc("min", "min(a, b)", "The smaller"),
                doc("N", "N(x)", "Evaluate numerically"),
                doc("N", "N(x, digits)", "Evaluate numerically to a given precision"));

        group(
                "Trigonometry",
                doc("sin", "sin(x)", "Sine"),
                doc("cos", "cos(x)", "Cosine"),
                doc("tan", "tan(x)", "Tangent"),
                doc("sec", "sec(x)", "Secant"),
                doc("csc", "csc(x)", "Cosecant"),
                doc("cot", "cot(x)", "Cotangent"),
                doc("arcsin", "arcsin(x)", "Inverse sine"),
                doc("arccos", "arccos(x)", "Inverse cosine"),
                doc("arctan", "arctan(x)", "Inverse tangent"),
                doc("sinh", "sinh(x)", "Hyperbolic sine — takes a number, not an angle"),
                doc("cosh", "cosh(x)", "Hyperbolic cosine"),
                doc("tanh", "tanh(x)", "Hyperbolic tangent"));

        group(
                "Numbers",
                doc("gcd", "gcd(a, b)", "Greatest common divisor"),
                doc("lcm", "lcm(a, b)", "Least common multiple"),
                doc("mod", "mod(a, b)", "Remainder"),
                doc("factorial", "factorial(n)", "n! — or write n! directly"),
                doc("binomial", "binomial(n, k)", "Binomial coefficient"),
                doc("PrimeQ", "PrimeQ(n)", "Is n prime?"),
                doc("Prime", "Prime(n)", "The n-th prime"),
                doc("FactorInteger", "FactorInteger(n)", "Prime factorisation"),
                doc("Fibonacci", "Fibonacci(n)", "The n-th Fibonacci number"),
                doc("Divisors", "Divisors(n)", "Every divisor of n"),
                doc("Zeta", "Zeta(s)", "Riemann zeta"));

        group(
                "Matrices",
                doc("dot", "dot(a, b)", "Matrix product — plain * is elementwise"),
                doc("Inverse", "Inverse(m)", "Matrix inverse"),
                doc("Det", "Det(m)", "Determinant"),
                doc("Transpose", "Transpose(m)", "Transpose"),
                doc("Eigenvalues", "Eigenvalues(m)", "Eigenvalues"),
                doc("IdentityMatrix", "IdentityMatrix(n)", "The n by n identity"),
                doc("MatrixRank", "MatrixRank(m)", "Rank"),
                doc("LinearSolve", "LinearSolve(a, b)", "Solve a linear system"));

        group(
                "Complex",
                doc("re", "re(z)", "Real part"),
                doc("im", "im(z)", "Imaginary part"),
                doc("conj", "conj(z)", "Complex conjugate"),
                doc("arg", "arg(z)", "Argument"));

        group(
                "Constants",
                doc("pi", "pi", "3.14159…"),
                doc("E", "E", "Euler's number — lower-case e stays a variable"),
                doc("I", "I", "The imaginary unit — lower-case i stays a variable"),
                doc("infinity", "infinity", "∞"));
    }

    private Functions() {}

    private static Doc doc(String name, String signature, String summary) {
        return new Doc(name, signature, summary, "");
    }

    private static void group(String heading, Doc... docs) {
        for (Doc d : docs) {
            ALL.add(new Doc(d.name(), d.signature(), d.summary(), heading));
        }
    }

    /** Everything, in table order. */
    public static List<Doc> all() {
        return List.copyOf(ALL);
    }

    /**
     * The entries whose name starts with {@code prefix}, case-insensitively.
     *
     * <p>Prefix rather than the palette's looser subsequence match: this completes a name being typed
     * into an expression, where an entry that merely shares some letters is noise. A blank prefix
     * matches nothing, so completion stays quiet until there is something to complete.
     */
    public static List<Doc> startingWith(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        String needle = prefix.toLowerCase(Locale.ROOT);
        List<Doc> hits = new ArrayList<>();
        for (Doc d : ALL) {
            if (d.name().toLowerCase(Locale.ROOT).startsWith(needle)) {
                hits.add(d);
            }
        }
        return hits;
    }

    /** The first entry for this exact name, or null. */
    public static Doc find(String name) {
        for (Doc d : ALL) {
            if (d.name().equalsIgnoreCase(name)) {
                return d;
            }
        }
        return null;
    }
}
