package com.calcula.parse;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.calcula.expr.Expr;
import com.calcula.expr.Expr.Call;
import com.calcula.expr.Expr.Flt;
import com.calcula.expr.Expr.Int;
import com.calcula.expr.Expr.Num;
import com.calcula.expr.Expr.Rat;
import com.calcula.expr.Expr.Sym;
import com.calcula.expr.Exprs;

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
    private static final int PREC_ADDITIVE = 3;
    private static final int PREC_MULTIPLICATIVE = 4;
    private static final int PREC_UNARY = 5;
    private static final int PREC_POWER = 6;
    private static final int PREC_POSTFIX = 7;
    private static final int PREC_ATOM = 100;

    private static final BigInteger MINUS_ONE = BigInteger.valueOf(-1);

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
                if (c.arity() == 2 && isMinusOne(c.arg(1))) {
                    return bracket(
                            "1/" + write(c.arg(0), PREC_MULTIPLICATIVE, true),
                            PREC_MULTIPLICATIVE,
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
            Expr negated = negatedPart(arg);
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
        List<Expr> numerator = new ArrayList<>();
        List<Expr> denominator = new ArrayList<>();
        boolean negative = false;
        for (Expr arg : args) {
            if (isMinusOne(arg)) {
                negative = !negative;
            } else if (arg instanceof Call p && "Power".equals(p.head()) && p.arity() == 2 && isMinusOne(p.arg(1))) {
                denominator.add(p.arg(0));
            } else if (arg instanceof Rat r) {
                // A rational coefficient is a division too. The engine returns pi^2/6 as
                // Times(1/6, Power(Pi, 2)), and nobody writes that as 1/6*pi^2.
                if (r.num().signum() < 0) {
                    negative = !negative;
                }
                BigInteger magnitude = r.num().abs();
                if (!magnitude.equals(BigInteger.ONE)) {
                    numerator.add(Exprs.of(magnitude));
                }
                denominator.add(Exprs.of(r.den()));
            } else {
                numerator.add(arg);
            }
        }
        String top = numerator.isEmpty() ? "1" : join(numerator, "*", PREC_MULTIPLICATIVE);
        String text = top;
        if (!denominator.isEmpty()) {
            // A multi-factor denominator must be bracketed: a/(b*c) is not a/b*c.
            String bottom = denominator.size() == 1
                    ? write(denominator.get(0), PREC_MULTIPLICATIVE, true)
                    : "(" + join(denominator, "*", PREC_MULTIPLICATIVE) + ")";
            text = top + "/" + bottom;
        }
        int prec = PREC_MULTIPLICATIVE;
        if (negative) {
            text = "-" + text;
            prec = PREC_UNARY;
        }
        return bracket(text, prec, parentPrec, weakSide);
    }

    private static String join(List<Expr> args, String separator, int prec) {
        return args.stream().map(a -> write(a, prec, false)).collect(Collectors.joining(separator));
    }

    /**
     * The positive part of a negated term, or null if it is not negated.
     *
     * <p>The coefficient is any negative literal, not just −1. The engine returns {@code -ln(u)/6} as
     * {@code Times(-1/6, Log(u))}, and matching only {@code Times(-1, …)} leaves it to print as
     * {@code + -ln(u)/6} — a stray sign that looks like a bug in the algebra rather than in the
     * formatter.
     */
    private static Expr negatedPart(Expr e) {
        if (e instanceof Call c
                && "Times".equals(c.head())
                && c.arity() >= 2
                && c.arg(0) instanceof Num n
                && negativeLiteral(n)) {
            List<Expr> rest = new ArrayList<>(c.args());
            Expr positive = negateLiteral(n);
            if (isOne(positive)) {
                rest.remove(0); // a bare -1 contributes nothing once the sign is extracted
            } else {
                rest.set(0, positive);
            }
            return rest.size() == 1 ? rest.get(0) : Exprs.call("Times", rest);
        }
        if (e instanceof Num n && negativeLiteral(n)) {
            return negateLiteral(n);
        }
        return null;
    }

    private static boolean isOne(Expr e) {
        return e instanceof Int i && i.value().equals(BigInteger.ONE);
    }

    private static boolean negativeLiteral(Num n) {
        return switch (n) {
            case Int i -> i.value().signum() < 0;
            case Rat r -> r.num().signum() < 0;
            case Flt f -> f.value().signum() < 0;
        };
    }

    private static Expr negateLiteral(Num n) {
        return switch (n) {
            case Int i -> Exprs.of(i.value().negate());
            case Rat r -> Exprs.rat(r.num().negate(), r.den());
            case Flt f -> Exprs.of(f.value().negate());
        };
    }

    private static boolean isMinusOne(Expr e) {
        return e instanceof Int i && i.value().equals(MINUS_ONE);
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

    private static int precedence(String head) {
        return switch (head) {
            case "Rule" -> PREC_RULE;
            case "Equal", "Less", "Greater", "LessEqual", "GreaterEqual", "Unequal" -> PREC_RELATION;
            case "Plus", "Subtract" -> PREC_ADDITIVE;
            case "Times", "Divide" -> PREC_MULTIPLICATIVE;
            case "Minus" -> PREC_UNARY;
            case "Power" -> PREC_POWER;
            case "Factorial" -> PREC_POSTFIX;
            default -> PREC_ATOM;
        };
    }
}
