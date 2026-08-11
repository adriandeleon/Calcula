package com.calcula.export;

import java.util.List;
import java.util.Map;

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
 * Presentation MathML for an expression.
 *
 * <p>Written here rather than taken from the engine's {@code MathMLForm}, for two concrete reasons: it
 * renders a matrix as nested {@code {…}} sets instead of an {@code <mtable>}, and it emits a MathML 2.0
 * DOCTYPE that has to be stripped before the markup can be embedded anywhere. Emitting from the tree
 * gives a clean fragment and a real table, and works with no engine loaded.
 *
 * <p>Element choice is not decoration — it is the whole content of the format. {@code <mi>} means
 * identifier and is rendered italic by default; {@code <mn>} means number and is upright; {@code <mo>}
 * means operator and carries its own spacing. Wrapping everything in {@code <mi>} produces markup that
 * renders as a font sample and pastes into Word as a row of variables.
 */
public final class MathmlWriter {

    private static final Map<String, String> SYMBOLS = Map.of(
            "Pi", "&#x3C0;",
            "Infinity", "&#x221E;",
            "Alpha", "&#x3B1;",
            "Beta", "&#x3B2;",
            "Theta", "&#x3B8;",
            "Lambda", "&#x3BB;");

    private static final Map<String, String> RELATIONS = Map.of(
            "Equal", "=",
            "Less", "&lt;",
            "Greater", "&gt;",
            "LessEqual", "&#x2264;",
            "GreaterEqual", "&#x2265;",
            "Unequal", "&#x2260;",
            "Rule", "&#x2192;");

    /** U+2212. The hyphen is a different, shorter character that sits at the wrong height. */
    private static final String MINUS = "&#x2212;";

    private MathmlWriter() {}

    /** A bare {@code <math>} element, ready to embed. No XML declaration and no DOCTYPE. */
    public static String write(Expr e) {
        return "<math xmlns=\"http://www.w3.org/1998/Math/MathML\" display=\"inline\">" + row(e) + "</math>";
    }

    /** The content without the wrapper, for nesting. */
    private static String row(Expr e) {
        String content = term(e);
        // An <mrow> groups; a single element does not need one, and the noise adds up quickly.
        return content;
    }

    private static String term(Expr e) {
        return switch (e) {
            case Int n ->
                n.value().signum() < 0
                        ? group("<mo>" + MINUS + "</mo><mn>" + n.value().abs() + "</mn>")
                        : "<mn>" + n.value() + "</mn>";
            case Flt f ->
                f.value().signum() < 0
                        ? group("<mo>" + MINUS + "</mo><mn>" + f.value().abs().toPlainString() + "</mn>")
                        : "<mn>" + f.value().toPlainString() + "</mn>";
            case Rat r -> {
                String fraction = "<mfrac><mn>" + r.num().abs() + "</mn><mn>" + r.den() + "</mn></mfrac>";
                yield r.num().signum() < 0 ? group("<mo>" + MINUS + "</mo>" + fraction) : fraction;
            }
            case Sym s -> symbol(s);
            case Call c -> call(c);
        };
    }

    private static String symbol(Sym s) {
        String entity = SYMBOLS.get(s.name());
        return "<mi>" + (entity != null ? entity : escape(Names.toDisplay(s.name()))) + "</mi>";
    }

    private static String call(Call c) {
        switch (c.head()) {
            case "Plus" -> {
                if (c.arity() >= 2) {
                    return group(sum(c.args()));
                }
            }
            case "Times" -> {
                if (c.arity() >= 2) {
                    return product(c.args());
                }
            }
            case "Divide" -> {
                if (c.arity() == 2) {
                    return "<mfrac>" + term(c.arg(0)) + term(c.arg(1)) + "</mfrac>";
                }
            }
            case "Subtract" -> {
                if (c.arity() == 2) {
                    return group(term(c.arg(0)) + "<mo>" + MINUS + "</mo>" + term(c.arg(1)));
                }
            }
            case "Minus" -> {
                if (c.arity() == 1) {
                    return group("<mo>" + MINUS + "</mo>" + term(c.arg(0)));
                }
            }
            case "Power" -> {
                return power(c);
            }
            case "Sqrt" -> {
                if (c.arity() == 1) {
                    return "<msqrt>" + term(c.arg(0)) + "</msqrt>";
                }
            }
            case "Abs" -> {
                if (c.arity() == 1) {
                    return group("<mo>|</mo>" + term(c.arg(0)) + "<mo>|</mo>");
                }
            }
            case "Factorial" -> {
                if (c.arity() == 1) {
                    return group(bracketed(c.arg(0)) + "<mo>!</mo>");
                }
            }
            case "List" -> {
                return list(c);
            }
            case "$Engine" -> {
                if (c.arity() == 1 && c.arg(0) instanceof Sym s) {
                    return "<mtext>" + escape(s.name()) + "</mtext>";
                }
            }
            default -> {
                // fall through
            }
        }
        String relation = RELATIONS.get(c.head());
        if (relation != null && c.arity() == 2) {
            return group(term(c.arg(0)) + "<mo>" + relation + "</mo>" + term(c.arg(1)));
        }
        return function(c);
    }

    private static String power(Call c) {
        if (c.arity() != 2) {
            return function(c);
        }
        if (Canonical.isMinusOne(c.arg(1))) {
            return "<mfrac><mn>1</mn>" + term(c.arg(0)) + "</mfrac>";
        }
        if (Canonical.isHalf(c.arg(1))) {
            return "<msqrt>" + term(c.arg(0)) + "</msqrt>";
        }
        return "<msup>" + bracketed(c.arg(0)) + term(c.arg(1)) + "</msup>";
    }

    private static String sum(List<Expr> args) {
        StringBuilder out = new StringBuilder(term(args.get(0)));
        for (Expr arg : args.subList(1, args.size())) {
            Expr negated = Canonical.negatedPart(arg);
            if (negated != null) {
                out.append("<mo>").append(MINUS).append("</mo>").append(term(negated));
            } else {
                out.append("<mo>+</mo>").append(term(arg));
            }
        }
        return out.toString();
    }

    private static String product(List<Expr> args) {
        Canonical.Product product = Canonical.splitProduct(args);
        String top = factors(product.numerator());
        String body = product.isFraction()
                ? "<mfrac>" + group(top) + group(factors(product.denominator())) + "</mfrac>"
                : group(top);
        return product.negative() ? group("<mo>" + MINUS + "</mo>" + body) : body;
    }

    private static String factors(List<Expr> factors) {
        if (factors.isEmpty()) {
            return "<mn>1</mn>";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < factors.size(); i++) {
            if (i > 0) {
                // An invisible-times operator between juxtaposed factors, and a visible dot between
                // numbers. U+2062 is what tells a reader — or a screen reader — that 2x is a product
                // rather than a two-character name.
                out.append(factors.get(i) instanceof Num ? "<mo>&#xB7;</mo>" : "<mo>&#x2062;</mo>");
            }
            // An <mrow> groups for parsing and draws NOTHING, so a sum inside a product needs real
            // parentheses or (1+x)*y renders as 1+x·y — wrong mathematics, not merely untidy.
            out.append(parenthesised(factors.get(i)));
        }
        return out.toString();
    }

    /** Wrapped in visible parentheses when it would otherwise be misread. */
    private static String parenthesised(Expr e) {
        return Canonical.needsParens(e) ? group("<mo>(</mo>" + term(e) + "<mo>)</mo>") : term(e);
    }

    private static String list(Call c) {
        if (Exprs.isMatrix(c)) {
            StringBuilder out = new StringBuilder("<mrow><mo>[</mo><mtable>");
            for (Expr rowExpr : c.args()) {
                out.append("<mtr>");
                for (Expr cell : Exprs.items(rowExpr)) {
                    out.append("<mtd>").append(term(cell)).append("</mtd>");
                }
                out.append("</mtr>");
            }
            return out.append("</mtable><mo>]</mo></mrow>").toString();
        }
        StringBuilder out = new StringBuilder("<mrow><mo>[</mo>");
        for (int i = 0; i < c.arity(); i++) {
            if (i > 0) {
                out.append("<mo>,</mo>");
            }
            out.append(term(c.arg(i)));
        }
        return out.append("<mo>]</mo></mrow>").toString();
    }

    private static String function(Call c) {
        StringBuilder out = new StringBuilder("<mrow>");
        // A function NAME is upright, which is <mi mathvariant="normal"> — an ordinary <mi> would set
        // it italic and make sin(x) read as s times i times n.
        out.append("<mi mathvariant=\"normal\">")
                .append(escape(Names.toDisplay(c.head())))
                .append("</mi><mo>&#x2061;</mo><mo>(</mo>");
        for (int i = 0; i < c.arity(); i++) {
            if (i > 0) {
                out.append("<mo>,</mo>");
            }
            out.append(term(c.arg(i)));
        }
        return out.append("<mo>)</mo></mrow>").toString();
    }

    /**
     * A superscript's base, parenthesised when compound.
     *
     * <p>Unlike a factor, a base needs brackets even around a fraction — {@code (a/b)^2} and
     * {@code a/b^2} are different numbers — so this asks a broader question than {@link #parenthesised}.
     */
    private static String bracketed(Expr e) {
        boolean compound = e instanceof Call c
                && switch (c.head()) {
                    case "Plus", "Subtract", "Times", "Minus", "Power", "Divide" -> true;
                    default -> false;
                };
        return compound ? group("<mo>(</mo>" + term(e) + "<mo>)</mo>") : term(e);
    }

    private static String group(String content) {
        return "<mrow>" + content + "</mrow>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
