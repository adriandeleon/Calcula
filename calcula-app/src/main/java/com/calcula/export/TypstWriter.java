package com.calcula.export;

import java.util.List;

import com.calcula.expr.Canonical;
import com.calcula.expr.Expr;
import com.calcula.expr.Expr.Call;
import com.calcula.expr.Expr.Flt;
import com.calcula.expr.Expr.Int;
import com.calcula.expr.Expr.Num;
import com.calcula.expr.Expr.Rat;
import com.calcula.expr.Expr.Sym;
import com.calcula.expr.Exprs;
import com.calcula.parse.Names;

/**
 * Typst maths markup for an expression.
 *
 * <p>Close enough to LaTeX to be mistaken for it and different in every detail that matters. Typst
 * writes a fraction as {@code (a)/(b)} rather than {@code \frac{a}{b}}, a root as {@code sqrt(a)}, a
 * function as a bare name, and it needs no backslashes — so this is a separate writer rather than a
 * translation of the TeX one.
 *
 * <p>The two rules that make the output correct rather than merely plausible:
 *
 * <ul>
 *   <li>A multi-character identifier is <b>quoted</b>. Typst maths reads bare letters as a product,
 *       so an unquoted {@code alpha} is <i>a·l·p·h·a</i> — except where the name IS one of Typst's
 *       own symbols, where quoting it would turn the symbol back into letters.
 *   <li>Division is written with brackets on both sides. {@code a/b + c} and {@code a/(b + c)} differ,
 *       and the parenthesisation cannot be inherited from how the tree happens to be shaped.
 * </ul>
 */
public final class TypstWriter {

    /** Names Typst already knows as symbols; quoting these would spell them out as letters. */
    private static final List<String> SYMBOLS = List.of(
            "pi", "alpha", "beta", "gamma", "delta", "theta", "lambda", "mu", "sigma", "phi", "omega", "infinity");

    private TypstWriter() {}

    /** A Typst maths fragment, without the surrounding {@code $ $}. */
    public static String write(Expr e) {
        return term(e);
    }

    /** Wrapped as inline maths, ready to paste into a Typst document. */
    public static String writeInline(Expr e) {
        return "$" + term(e) + "$";
    }

    private static String term(Expr e) {
        return switch (e) {
            case Int n -> n.value().toString();
            case Flt f -> f.value().toPlainString();
            case Rat r -> {
                String fraction = "(" + r.num().abs() + ")/(" + r.den() + ")";
                yield r.num().signum() < 0 ? "-" + fraction : fraction;
            }
            case Sym s -> symbol(s.name());
            case Call c -> call(c);
        };
    }

    private static String symbol(String stored) {
        String shown = Names.toDisplay(stored);
        if (SYMBOLS.contains(shown.toLowerCase(java.util.Locale.ROOT))) {
            return shown.toLowerCase(java.util.Locale.ROOT);
        }
        // Bare letters are a PRODUCT in Typst maths, so anything longer than one character is quoted
        // or it is silently rendered as its own letters multiplied together.
        return shown.length() == 1 ? shown : "\"" + shown + "\"";
    }

    private static String call(Call c) {
        switch (c.head()) {
            case "Plus" -> {
                if (c.arity() >= 2) {
                    return sum(c);
                }
            }
            case "Times" -> {
                if (c.arity() >= 2) {
                    return product(c);
                }
            }
            case "Divide" -> {
                if (c.arity() == 2) {
                    return "(" + term(c.arg(0)) + ")/(" + term(c.arg(1)) + ")";
                }
            }
            case "Subtract" -> {
                if (c.arity() == 2) {
                    return term(c.arg(0)) + " - " + subtrahend(c.arg(1));
                }
            }
            case "Minus" -> {
                if (c.arity() == 1) {
                    return "-" + bracketed(c.arg(0));
                }
            }
            case "Power" -> {
                return power(c);
            }
            case "Sqrt" -> {
                if (c.arity() == 1) {
                    return "sqrt(" + term(c.arg(0)) + ")";
                }
            }
            case "Abs" -> {
                if (c.arity() == 1) {
                    return "abs(" + term(c.arg(0)) + ")";
                }
            }
            case "Factorial" -> {
                if (c.arity() == 1) {
                    return bracketed(c.arg(0)) + "!";
                }
            }
            case "List" -> {
                return list(c);
            }
            case "Equal" -> {
                if (c.arity() == 2) {
                    return term(c.arg(0)) + " = " + term(c.arg(1));
                }
            }
            default -> {
                // fall through to a plain function call
            }
        }
        return function(c);
    }

    private static String power(Call c) {
        if (c.arity() != 2) {
            return function(c);
        }
        if (Canonical.isMinusOne(c.arg(1))) {
            return "1/(" + term(c.arg(0)) + ")";
        }
        if (Canonical.isHalf(c.arg(1))) {
            return "sqrt(" + term(c.arg(0)) + ")";
        }
        return bracketed(c.arg(0)) + "^(" + term(c.arg(1)) + ")";
    }

    private static String sum(Call c) {
        StringBuilder out = new StringBuilder(term(c.arg(0)));
        for (Expr arg : c.args().subList(1, c.arity())) {
            Expr negated = Canonical.negatedPart(arg);
            out.append(negated != null ? " - " + term(negated) : " + " + term(arg));
        }
        return out.toString();
    }

    private static String product(Call c) {
        Canonical.Product product = Canonical.splitProduct(c.args());
        String top = factors(product.numerator());
        String body = product.isFraction() ? "(" + top + ")/(" + factors(product.denominator()) + ")" : top;
        return product.negative() ? "-" + body : body;
    }

    private static String factors(List<Expr> factors) {
        if (factors.isEmpty()) {
            return "1";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < factors.size(); i++) {
            if (i > 0) {
                // A space is juxtaposition in Typst; a dot is only needed between two numbers, where
                // juxtaposition would read as one longer number.
                out.append(factors.get(i) instanceof Num ? " dot " : " ");
            }
            out.append(bracketed(factors.get(i)));
        }
        return out.toString();
    }

    private static String list(Call c) {
        if (Exprs.isMatrix(c)) {
            StringBuilder out = new StringBuilder("mat(");
            for (int r = 0; r < c.arity(); r++) {
                if (r > 0) {
                    out.append("; ");
                }
                List<Expr> cells = Exprs.items(c.arg(r));
                for (int i = 0; i < cells.size(); i++) {
                    out.append(i > 0 ? ", " : "").append(term(cells.get(i)));
                }
            }
            return out.append(")").toString();
        }
        StringBuilder out = new StringBuilder("vec(");
        for (int i = 0; i < c.arity(); i++) {
            out.append(i > 0 ? ", " : "").append(term(c.arg(i)));
        }
        return out.append(")").toString();
    }

    private static String function(Call c) {
        StringBuilder out = new StringBuilder(symbol(c.head())).append("(");
        for (int i = 0; i < c.arity(); i++) {
            out.append(i > 0 ? ", " : "").append(term(c.arg(i)));
        }
        return out.append(")").toString();
    }

    /**
     * The right-hand side of a minus, bracketed only when it is itself additive.
     *
     * <p>Narrower than {@link #bracketed} on purpose. Subtraction only loses its meaning to a sum:
     * {@code a - (b + c)} really is different from {@code a - b + c}, whereas {@code a - (x^2)} is
     * merely noisy — and noise is the thing this application exists to avoid.
     */
    private static String subtrahend(Expr e) {
        boolean additive = e instanceof Call c
                && switch (c.head()) {
                    case "Plus", "Subtract" -> c.arity() >= 2;
                    case "Minus" -> c.arity() == 1;
                    default -> false;
                };
        return additive || (e instanceof Num n && Canonical.isNegative(n)) ? "(" + term(e) + ")" : term(e);
    }

    /** Bracketed when it would otherwise bind differently than it reads. */
    private static String bracketed(Expr e) {
        return Canonical.needsParens(e) ? "(" + term(e) + ")" : term(e);
    }
}
