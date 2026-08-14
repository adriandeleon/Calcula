package com.calcula.parse;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import com.calcula.expr.Arith;
import com.calcula.expr.Canonical;
import com.calcula.expr.Expr;
import com.calcula.expr.Expr.Call;
import com.calcula.expr.Expr.Flt;
import com.calcula.expr.Expr.Int;
import com.calcula.expr.Expr.Num;
import com.calcula.expr.Expr.Rat;
import com.calcula.expr.Expr.Sym;
import com.calcula.expr.Exprs;
import com.calcula.hms.HmsForm;
import com.calcula.units.Units;

/**
 * Renders an {@link Expr} back to the notation {@link Parser} accepts, with the fewest parentheses that
 * still mean the same thing.
 *
 * <p>Minimal parenthesisation is not cosmetic: {@code a - (b - c)} and {@code a - b - c} are different
 * numbers, so a formatter that drops parens by precedence alone is a correctness bug, not an ugly one.
 * The rule is that a child needs bracketing when it binds more loosely than its parent, OR binds equally
 * but sits on the associativity's weak side — the right of a left-associative operator, the left of a
 * right-associative one.
 *
 * <h2>Canonical forms</h2>
 *
 * <p>Results arrive in the engine's canonical shape, which is not how anyone writes mathematics. It has
 * no subtraction, no division and no unary minus: {@code a - b} is {@code Plus(a, Times(-1, b))},
 * {@code a/b} is {@code Times(a, Power(b, -1))}, and {@code Plus} and {@code Times} are n-ary. Printing
 * that literally gives {@code x*y^(-1)} and {@code 2 + (-1)*x}, so this reassembles the familiar
 * notation on the way out. It is display sugar only — the tree is untouched — but without it every
 * answer the engine returns reads like machine output.
 *
 * <h2>The round-trip invariant</h2>
 *
 * <p>{@code parse(format(parse(s))) == parse(s)}: formatting is stable and lossless over everything the
 * parser can build. It is deliberately not stated over arbitrary trees, because numeric literals
 * normalise on the way back — a {@link Rat} prints as a quotient and re-parses as a division — and
 * because the sugar above is a projection rather than a bijection.
 *
 * <p>A negative literal is treated as though it had unary precedence, not atomic. Printing {@code
 * Factorial(-3)} as {@code -3!} would re-parse as {@code -(3!)}, since {@code !} binds tighter than the
 * sign — a wrong answer produced entirely by formatting.
 */
public final class Formatter {

    private static final int PREC_RULE = 1;
    private static final int PREC_RELATION = 2;

    /** Between a comparison and a sum, which is exactly where the parser puts it. */
    private static final int PREC_ERROR = 3;

    private static final int PREC_ADDITIVE = 4;
    private static final int PREC_MULTIPLICATIVE = 5;
    private static final int PREC_UNARY = 6;
    private static final int PREC_POWER = 7;
    private static final int PREC_POSTFIX = 8;
    private static final int PREC_ATOM = 100;

    private Formatter() {}

    public static String format(Expr e) {
        return write(e, 0, false);
    }

    private static String write(Expr e, int parentPrec, boolean weakSide) {
        return switch (e) {
            // A negative literal carries a leading sign, which binds as loosely as any unary minus.
            case Int n -> bracket(n.value().toString(), signPrec(n.value().signum(), PREC_ATOM), parentPrec, weakSide);
            case Flt f ->
                bracket(f.value().toPlainString(), signPrec(f.value().signum(), PREC_ATOM), parentPrec, weakSide);
            // A rational prints as a division, which is also how it parses back.
            case Rat r ->
                bracket(r.num() + "/" + r.den(), signPrec(r.num().signum(), PREC_MULTIPLICATIVE), parentPrec, weakSide);
            case Sym s -> Names.toDisplay(s.name());
            case Call c -> call(c, parentPrec, weakSide);
        };
    }

    private static int signPrec(int signum, int whenPositive) {
        return signum < 0 ? PREC_UNARY : whenPositive;
    }

    private static String call(Call c, int parentPrec, boolean weakSide) {
        // Ahead of everything: a pattern is an atom to read even though it is a call to hold, and
        // printing it as Pattern(x, Blank()) would be a form the parser can read back but nobody
        // would type.
        String pattern = Patterns.write(c);
        if (pattern != null) {
            return pattern;
        }
        switch (c.head()) {
            case "Plus" -> {
                if (c.arity() >= 2) {
                    return bracket(plus(c.args()), PREC_ADDITIVE, parentPrec, weakSide);
                }
            }
            case "Times" -> {
                if (c.arity() >= 2) {
                    return times(c.args(), parentPrec, weakSide);
                }
            }
            case "Power" -> {
                // Power(b, -1) is how the engine spells a reciprocal.
                if (c.arity() == 2 && Canonical.isMinusOne(c.arg(1))) {
                    return bracket(
                            "1/" + write(c.arg(0), PREC_MULTIPLICATIVE, true),
                            PREC_MULTIPLICATIVE,
                            parentPrec,
                            weakSide);
                }
            }
            case "Interval" -> {
                Expr bounds = c.arity() == 1 ? c.arg(0) : null;
                if (bounds instanceof Call pair && Exprs.isList(pair) && pair.arity() == 2) {
                    return bracket(
                            write(pair.arg(0), PREC_ERROR, false) + " .. " + write(pair.arg(1), PREC_ERROR, true),
                            PREC_ERROR,
                            parentPrec,
                            weakSide);
                }
            }
            case "Modulo" -> {
                if (c.arity() == 2) {
                    return bracket(
                            write(c.arg(0), PREC_ERROR, false) + " mod " + write(c.arg(1), PREC_ERROR, true),
                            PREC_ERROR,
                            parentPrec,
                            weakSide);
                }
            }
            case "Quantity" -> {
                // `3 m`, which is what was typed. Quantity(3, m) is the engine's spelling and nobody
                // types it, so printing that back would make the round trip a thing only a machine
                // could read.
                // Only for a unit the parser will read back. A compound one comes out of the engine
                // as a single name — `m^2`, `m*s^-1` — and `12 m^2` reads back as (12 m)^2, which is
                // 144 square metres: a wrong answer produced by printing, and silently, at save time.
                // The engine's own spelling is ugly and stable, and stable is the one that matters.
                if (c.arity() == 2
                        && c.arg(0) instanceof Num value
                        && c.arg(1) instanceof Sym unit
                        && Units.isUnit(unit.name())) {
                    return bracket(
                            write(value, PREC_MULTIPLICATIVE, false) + " " + unit.name(),
                            PREC_MULTIPLICATIVE,
                            parentPrec,
                            weakSide);
                }
            }
            case "HMS" -> {
                // Rendered by the form itself rather than by writing the three parts out here, so the
                // one leading minus and the carry past sixty cannot drift from what the arithmetic does.
                HmsForm duration = durationOf(c);
                if (duration != null) {
                    return bracket(duration.format(), signPrec(duration.signum(), PREC_ATOM), parentPrec, weakSide);
                }
            }
            case "PlusMinus" -> {
                if (c.arity() == 2) {
                    // Its own precedence, between a comparison and a sum, matching where it parses.
                    return bracket(
                            write(c.arg(0), PREC_ERROR, false) + " +/- " + write(c.arg(1), PREC_ERROR, true),
                            PREC_ERROR,
                            parentPrec,
                            weakSide);
                }
            }
            case "Minus" -> {
                if (c.arity() == 1) {
                    return bracket("-" + write(c.arg(0), PREC_UNARY, false), PREC_UNARY, parentPrec, weakSide);
                }
            }
            case "Factorial" -> {
                if (c.arity() == 1) {
                    return bracket(write(c.arg(0), PREC_POSTFIX, false) + "!", PREC_POSTFIX, parentPrec, weakSide);
                }
            }
            case "$Engine" -> {
                // An engine value we do not model. Show its own printed form rather than the wrapper:
                // the encoding is an implementation detail, not something to put in front of a user.
                if (c.arity() == 1 && c.arg(0) instanceof Sym s) {
                    return s.name();
                }
            }
            default -> {
                // fall through to the generic handling below
            }
        }
        String infix = infixOperator(c.head());
        if (infix != null && c.arity() == 2) {
            int prec = precedence(c.head());
            boolean rightAssociative = "Power".equals(c.head()) || "Rule".equals(c.head());
            String left = write(c.arg(0), prec, rightAssociative);
            String right = write(c.arg(1), prec, !rightAssociative);
            // Tight for the multiplicative operators and the power, spaced for sums and relations:
            // the spacing mirrors precedence. It also has to MATCH what the n-ary Times path emits,
            // or `a/b` and a parsed `a / b` — the same quotient — print two different ways.
            String text = tight(c.head()) ? left + infix + right : left + " " + infix + " " + right;
            return bracket(text, prec, parentPrec, weakSide);
        }
        if (Exprs.LIST.equals(c.head())) {
            return c.args().stream().map(Formatter::format).collect(Collectors.joining(", ", "[", "]"));
        }
        return Names.toDisplay(c.head())
                + c.args().stream().map(Formatter::format).collect(Collectors.joining(", ", "(", ")"));
    }

    /** n-ary sum, turning a negated term back into a subtraction so {@code a + (-1)*b} reads as {@code a - b}. */
    private static String plus(List<Expr> args) {
        StringBuilder out = new StringBuilder(write(args.get(0), PREC_ADDITIVE, false));
        for (Expr arg : args.subList(1, args.size())) {
            Expr negated = Canonical.negatedPart(arg);
            if (negated != null) {
                out.append(" - ").append(write(negated, PREC_ADDITIVE, true));
            } else {
                out.append(" + ").append(write(arg, PREC_ADDITIVE, false));
            }
        }
        return out.toString();
    }

    /**
     * n-ary product, splitting reciprocal factors back out into a denominator. Without this, {@code x/y}
     * comes back from the engine and prints as {@code x*y^(-1)}.
     */
    private static String times(List<Expr> args, int parentPrec, boolean weakSide) {
        Canonical.Product product = Canonical.splitProduct(args);
        List<Expr> numerator = product.numerator();
        String top = numerator.isEmpty() ? "1" : join(numerator, "*", PREC_MULTIPLICATIVE);
        String text = top;
        if (product.isFraction()) {
            // A multi-factor denominator must be bracketed: a/(b*c) is not a/b*c.
            List<Expr> denominator = product.denominator();
            String bottom = denominator.size() == 1
                    ? write(denominator.get(0), PREC_MULTIPLICATIVE, true)
                    : "(" + join(denominator, "*", PREC_MULTIPLICATIVE) + ")";
            text = top + "/" + bottom;
        }
        int prec = PREC_MULTIPLICATIVE;
        if (product.negative()) {
            text = "-" + text;
            prec = PREC_UNARY;
        }
        return bracket(text, prec, parentPrec, weakSide);
    }

    private static String join(List<Expr> args, String separator, int prec) {
        return args.stream().map(a -> write(a, prec, false)).collect(Collectors.joining(separator));
    }

    /** Wrap only when precedence demands it, or when sitting on the weak side of an equal-precedence parent. */
    private static String bracket(String text, int prec, int parentPrec, boolean weakSide) {
        boolean needed = prec < parentPrec || (prec == parentPrec && weakSide);
        return needed ? "(" + text + ")" : text;
    }

    /** Operators set without surrounding space. */
    private static boolean tight(String head) {
        return switch (head) {
            case "Times", "Divide", "Power" -> true;
            default -> false;
        };
    }

    private static String infixOperator(String head) {
        return switch (head) {
            case "Plus" -> "+";
            case "Subtract" -> "-";
            case "Times" -> "*";
            case "Divide" -> "/";
            case "Power" -> "^";
            case "Equal" -> "=";
            case "Less" -> "<";
            case "Greater" -> ">";
            case "LessEqual" -> "<=";
            case "GreaterEqual" -> ">=";
            case "Unequal" -> "!=";
            case "Rule" -> "->";
            default -> null;
        };
    }

    /** The three parts as a duration, or null when they are not all numbers. */
    private static HmsForm durationOf(Call c) {
        if (c.arity() != 3) {
            return null;
        }
        BigDecimal[] parts = new BigDecimal[3];
        for (int i = 0; i < 3; i++) {
            if (!(c.arg(i) instanceof Num n)) {
                return null;
            }
            parts[i] = Arith.toDecimal(n, Arith.DEFAULT_PRECISION);
        }
        return HmsForm.ofParts(parts[0], parts[1], parts[2]);
    }

    private static int precedence(String head) {
        return switch (head) {
            case "Rule" -> PREC_RULE;
            case "Equal", "Less", "Greater", "LessEqual", "GreaterEqual", "Unequal" -> PREC_RELATION;
            case "PlusMinus", "Interval", "Modulo" -> PREC_ERROR;
            case "Quantity" -> PREC_MULTIPLICATIVE;
            case "Plus", "Subtract" -> PREC_ADDITIVE;
            case "Times", "Divide" -> PREC_MULTIPLICATIVE;
            case "Minus" -> PREC_UNARY;
            case "Power" -> PREC_POWER;
            case "Factorial" -> PREC_POSTFIX;
            default -> PREC_ATOM;
        };
    }
}
