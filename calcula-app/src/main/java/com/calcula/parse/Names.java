package com.calcula.parse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translation between what a user types and the head stored in the tree.
 *
 * <p>The tree uses the engine's names ({@code Sin}, {@code ArcTan}, {@code Log}) so the adapter stays a
 * rename rather than a translation table. Users type {@code sin(x)}, so this maps between the two, in
 * both directions.
 *
 * <p>An unknown name passes through <em>verbatim, preserving case</em>. That is what lets a function the
 * engine knows and we have never heard of still be callable, and it is the same totality argument that
 * shapes {@link com.calcula.expr.Expr}.
 */
public final class Names {

    /** What the user types → what the tree stores. Insertion order matters for the reverse map. */
    private static final Map<String, String> TO_HEAD = new LinkedHashMap<>();

    /** What the tree stores → what we print. First registration wins, so {@code Log} prints as {@code ln}. */
    private static final Map<String, String> TO_DISPLAY = new LinkedHashMap<>();

    static {
        // Trigonometry
        map("sin", "Sin");
        map("cos", "Cos");
        map("tan", "Tan");
        map("sec", "Sec");
        map("csc", "Csc");
        map("cot", "Cot");
        // arc* is registered first so that is how these print back — the mathematical spelling rather
        // than the C-library one, this being a mathematics tool.
        map("arcsin", "ArcSin");
        map("arccos", "ArcCos");
        map("arctan", "ArcTan");
        alias("asin", "ArcSin");
        alias("acos", "ArcCos");
        alias("atan", "ArcTan");
        map("sinh", "Sinh");
        map("cosh", "Cosh");
        map("tanh", "Tanh");
        map("arcsinh", "ArcSinh");
        map("arccosh", "ArcCosh");
        map("arctanh", "ArcTanh");
        alias("asinh", "ArcSinh");
        alias("acosh", "ArcCosh");
        alias("atanh", "ArcTanh");

        // Exponential and logarithmic. `ln` is registered first so Log prints back as ln; `log` is
        // accepted as an alias for the NATURAL log, following CAS convention rather than the
        // pocket-calculator one. log10 and log2 are spelled out to remove the ambiguity entirely.
        map("exp", "Exp");
        map("ln", "Log");
        alias("log", "Log");
        map("log10", "Log10");
        map("log2", "Log2");
        map("sqrt", "Sqrt");

        // Numeric
        map("abs", "Abs");
        map("sign", "Sign");
        map("floor", "Floor");
        map("ceil", "Ceiling");
        alias("ceiling", "Ceiling");
        map("round", "Round");
        map("max", "Max");
        map("min", "Min");
        map("gcd", "GCD");
        map("lcm", "LCM");
        map("mod", "Mod");
        // Matrix/vector product. `*` is elementwise here, as in Mathematica.
        map("dot", "Dot");
        map("factorial", "Factorial");
        map("binomial", "Binomial");

        // Complex
        map("re", "Re");
        map("im", "Im");
        map("conj", "Conjugate");
        map("arg", "Arg");

        // Algebra — the whole reason the engine is here
        // NOT aliased to `d`: a single letter d is a variable far more often than it is a
        // derivative, and claiming it renders `c - d` as `c − deriv`.
        map("deriv", "D");
        map("integrate", "Integrate");
        map("solve", "Solve");
        map("simplify", "Simplify");
        map("expand", "Expand");
        map("factor", "Factor");
        map("limit", "Limit");
        map("sum", "Sum");
        map("product", "Product");

        // Money. Spreadsheet spellings, because that is where everybody has met them, and no CAS in
        // this build implements any of them -- see Builtins.
        map("pv", "PresentValue");
        map("fv", "FutureValue");
        map("pmt", "Payment");
        map("nper", "Periods");
        map("rate", "Rate");
        map("npv", "NetPresentValue");
        map("sln", "StraightLineDepreciation");
        map("syd", "SumOfYearsDepreciation");
        map("ddb", "DecliningBalanceDepreciation");

        // Constants. `pi` is safe to claim; `e` and `i` deliberately are NOT, because they are far too
        // useful as ordinary variable names — an index `i` is more common in practice than the
        // imaginary unit. Type E, I or Pi for the constants; those are already the stored names.
        map("pi", "Pi");
        // Registered as a MAP, not an alias, so it prints back the way it is typed — the same rule pi
        // follows. As an alias it was accepted on input and printed as `Infinity`, which is the one
        // spelling nobody used.
        map("infinity", "Infinity");
        alias("inf", "Infinity");
    }

    private Names() {}

    /** Register a name in both directions. */
    private static void map(String display, String head) {
        TO_HEAD.put(display, head);
        TO_DISPLAY.putIfAbsent(head, display);
    }

    /** Register an accepted spelling that is not how we print it back. */
    private static void alias(String display, String head) {
        TO_HEAD.put(display, head);
    }

    /** The stored head for a typed name; the name itself when unknown. */
    public static String toHead(String typed) {
        String head = TO_HEAD.get(typed);
        if (head != null) {
            return head;
        }
        // Also accept the stored spelling directly, so Sin(x) and ArcTan(x) work as typed.
        return typed;
    }

    /** How to print a stored head; the head itself when we have no friendlier spelling. */
    public static String toDisplay(String head) {
        return TO_DISPLAY.getOrDefault(head, head);
    }

    /** True when this typed name is one we translate — used by tests and later by completion. */
    public static boolean isKnown(String typed) {
        return TO_HEAD.containsKey(typed);
    }
}
