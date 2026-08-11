package com.calcula.export;

import java.util.List;
import java.util.Map;

import com.calcula.expr.Canonical;
import com.calcula.expr.Expr;
import com.calcula.expr.Expr.Call;
import com.calcula.expr.Expr.Flt;
import com.calcula.expr.Expr.Int;
import com.calcula.expr.Expr.Rat;
import com.calcula.expr.Expr.Sym;
import com.calcula.expr.Exprs;

/**
 * LaTeX for an expression.
 *
 * <p>Written here rather than taken from the engine's {@code TeXForm}, which produces correct but noisy
 * output: an explicit {@code \cdot} between every factor, {@code {x}^{2}} for {@code x^2}, and a
 * redundant {@code \left(\right)} inside every {@code \sqrt}. Since the tree is already walked for
 * display, emitting TeX from the same walk costs one more visitor and gives output a person would have
 * written — and it works with no engine loaded.
 *
 * <p>Braces are emitted around any group of more than one character, which is the rule that keeps
 * {@code x^{10}} from becoming {@code x^10} — silently {@code x¹⁰} rendered as {@code x¹0}.
 */
public final class TexWriter {

    /** Functions with a reserved TeX macro; anything else is set upright with {@code \operatorname}. */
    private static final Map<String, String> MACROS = Map.ofEntries(
            Map.entry("Sin", "\\sin"),
            Map.entry("Cos", "\\cos"),
            Map.entry("Tan", "\\tan"),
            Map.entry("Sec", "\\sec"),
            Map.entry("Csc", "\\csc"),
            Map.entry("Cot", "\\cot"),
            Map.entry("ArcSin", "\\arcsin"),
            Map.entry("ArcCos", "\\arccos"),
            Map.entry("ArcTan", "\\arctan"),
            Map.entry("Sinh", "\\sinh"),
            Map.entry("Cosh", "\\cosh"),
            Map.entry("Tanh", "\\tanh"),
            Map.entry("Exp", "\\exp"),
            Map.entry("Log", "\\ln"),
            Map.entry("Log10", "\\log_{10}"),
            Map.entry("Max", "\\max"),
            Map.entry("Min", "\\min"));

    private static final Map<String, String> SYMBOLS = Map.of(
            "Pi", "\\pi",
            "Infinity", "\\infty",
            "E", "e",
            "I", "i",
            "Alpha", "\\alpha",
            "Beta", "\\beta",
            "Theta", "\\theta",
            "Lambda", "\\lambda",
            "Degree", "^\\circ");

    private static final Map<String, String> RELATIONS = Map.of(
            "Equal", "=",
            "Less", "<",
            "Greater", ">",
            "LessEqual", "\\le",
            "GreaterEqual", "\\ge",
            "Unequal", "\\ne",
            "Rule", "\\to");

    private TexWriter() {}

    public static String write(Expr e) {
        return term(e, false);
    }

    /**
     * @param bracketed whether the caller needs this wrapped when it is compound — a sum under a
     *     superscript, say
     */
    private static String term(Expr e, boolean bracketed) {
        String text =
                switch (e) {
                    case Int n -> n.value().toString();
                    case Flt f -> f.value().toPlainString();
                    // The sign goes outside the fraction, where a reader expects it.
                    case Rat r ->
                        (r.num().signum() < 0 ? "-" : "") + "\\frac{" + r.num().abs() + "}{" + r.den() + "}";
                    case Sym s -> symbol(s);
                    case Call c -> call(c);
                };
        return bracketed && Canonical.needsParens(e) ? "\\left(" + text + "\\right)" : text;
    }

    private static String symbol(Sym s) {
        String macro = SYMBOLS.get(s.name());
        if (macro != null) {
            return macro;
        }
        // A multi-letter variable set in maths italic renders as a product of its letters, which is
        // why TeX has \mathit — `speed` should not look like s times p times e times e times d.
        return s.name().length() == 1 ? s.name() : "\\mathit{" + s.name() + "}";
    }

    private static String call(Call c) {
        switch (c.head()) {
            case "Plus" -> {
                if (c.arity() >= 2) {
                    return sum(c.args());
                }
            }
            case "Times" -> {
                if (c.arity() >= 2) {
                    return product(c.args());
                }
            }
            case "Divide" -> {
                if (c.arity() == 2) {
                    return "\\frac{" + write(c.arg(0)) + "}{" + write(c.arg(1)) + "}";
                }
            }
            case "Subtract" -> {
                if (c.arity() == 2) {
                    return term(c.arg(0), false) + " - " + term(c.arg(1), false);
                }
            }
            case "Minus" -> {
                if (c.arity() == 1) {
                    return "-" + term(c.arg(0), false);
                }
            }
            case "Power" -> {
                return power(c);
            }
            case "Sqrt" -> {
                if (c.arity() == 1) {
                    return "\\sqrt{" + write(c.arg(0)) + "}";
                }
            }
            case "Factorial" -> {
                if (c.arity() == 1) {
                    return term(c.arg(0), true) + "!";
                }
            }
            case "Abs" -> {
                if (c.arity() == 1) {
                    return "\\left|" + write(c.arg(0)) + "\\right|";
                }
            }
            case "List" -> {
                return list(c);
            }
            case "$Engine" -> {
                if (c.arity() == 1 && c.arg(0) instanceof Sym s) {
                    return "\\mathtt{" + escape(s.name()) + "}";
                }
            }
            default -> {
                // fall through
            }
        }
        String relation = RELATIONS.get(c.head());
        if (relation != null && c.arity() == 2) {
            return term(c.arg(0), false) + " " + relation + " " + term(c.arg(1), false);
        }
        return function(c);
    }

    private static String power(Call c) {
        if (c.arity() != 2) {
            return function(c);
        }
        if (Canonical.isMinusOne(c.arg(1))) {
            return "\\frac{1}{" + write(c.arg(0)) + "}";
        }
        if (Canonical.isHalf(c.arg(1))) {
            return "\\sqrt{" + write(c.arg(0)) + "}";
        }
        // Braces around the exponent whatever it is: x^{10} is right and x^10 is x to the one,
        // followed by a zero.
        return term(c.arg(0), true) + "^{" + write(c.arg(1)) + "}";
    }

    private static String sum(List<Expr> args) {
        StringBuilder out = new StringBuilder(term(args.get(0), false));
        for (Expr arg : args.subList(1, args.size())) {
            Expr negated = Canonical.negatedPart(arg);
            out.append(negated != null ? " - " + term(negated, false) : " + " + term(arg, false));
        }
        return out.toString();
    }

    private static String product(List<Expr> args) {
        Canonical.Product product = Canonical.splitProduct(args);
        // Inside \frac the braces already group, so bracketing there produces exactly the noise this
        // writer exists to avoid: \frac{\left(1 + x\right)}{…}. Juxtaposed factors DO need brackets.
        String text = product.isFraction()
                ? "\\frac{" + factors(product.numerator(), false) + "}{" + factors(product.denominator(), false) + "}"
                : factors(product.numerator(), true);
        return product.negative() ? "-" + text : text;
    }

    private static String factors(List<Expr> factors, boolean bracketCompound) {
        if (factors.isEmpty()) {
            return "1";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < factors.size(); i++) {
            if (i > 0) {
                // A dot only where juxtaposition would misread — between two numbers, where 2\,3
                // would look like twenty-three.
                out.append(factors.get(i) instanceof Expr.Num ? " \\cdot " : " ");
            }
            out.append(term(factors.get(i), bracketCompound));
        }
        return out.toString();
    }

    private static String list(Call c) {
        if (Exprs.isMatrix(c)) {
            StringBuilder out = new StringBuilder("\\begin{pmatrix}");
            List<Expr> rows = c.args();
            for (int r = 0; r < rows.size(); r++) {
                if (r > 0) {
                    out.append(" \\\\ ");
                }
                List<Expr> cells = Exprs.items(rows.get(r));
                for (int i = 0; i < cells.size(); i++) {
                    out.append(i > 0 ? " & " : "").append(write(cells.get(i)));
                }
            }
            return out.append("\\end{pmatrix}").toString();
        }
        StringBuilder out = new StringBuilder("\\left[");
        for (int i = 0; i < c.arity(); i++) {
            out.append(i > 0 ? ", " : "").append(write(c.arg(i)));
        }
        return out.append("\\right]").toString();
    }

    private static String function(Call c) {
        String macro = MACROS.get(c.head());
        StringBuilder out = new StringBuilder(
                macro != null ? macro : "\\operatorname{" + escape(com.calcula.parse.Names.toDisplay(c.head())) + "}");
        out.append("\\left(");
        for (int i = 0; i < c.arity(); i++) {
            out.append(i > 0 ? ", " : "").append(write(c.arg(i)));
        }
        return out.append("\\right)").toString();
    }

    /** The characters TeX treats as markup. */
    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            switch (ch) {
                case '\\' -> out.append("\\textbackslash{}");
                case '&', '%', '$', '#', '_', '{', '}' -> out.append('\\').append(ch);
                case '~' -> out.append("\\textasciitilde{}");
                case '^' -> out.append("\\textasciicircum{}");
                default -> out.append(ch);
            }
        }
        return out.toString();
    }
}
