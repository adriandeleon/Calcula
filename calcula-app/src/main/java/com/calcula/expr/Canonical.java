package com.calcula.expr;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.calcula.expr.Expr.Call;
import com.calcula.expr.Expr.Flt;
import com.calcula.expr.Expr.Int;
import com.calcula.expr.Expr.Num;
import com.calcula.expr.Expr.Rat;

/**
 * Reading the engine's canonical forms back as the notation people write.
 *
 * <p>Results arrive with no subtraction, no division and no unary minus, and with n-ary {@code Plus}
 * and {@code Times}: {@code a - b} is {@code Plus(a, Times(-1, b))}, {@code a/b} is
 * {@code Times(a, Power(b, -1))}, and a rational coefficient stands in for a division. Every renderer
 * has to undo that, or a quotient comes out as a negative exponent.
 *
 * <p>It lives here, once, because there are now four renderers that need it — the text formatter, the
 * on-screen layout, and the TeX and MathML writers. Four copies of "is this a negated term" is four
 * chances to disagree, and they would disagree silently: a fix applied to one would leave the other
 * three still printing {@code a + -ln(u)/6}.
 */
public final class Canonical {

    private static final BigInteger MINUS_ONE = BigInteger.valueOf(-1);
    private static final BigInteger TWO = BigInteger.valueOf(2);

    private Canonical() {}

    /**
     * A product, separated into the parts a reader expects to see.
     *
     * @param negative whether an odd number of negative factors was extracted
     * @param numerator factors on top; empty means 1
     * @param denominator factors underneath; empty means there is no division
     */
    public record Product(boolean negative, List<Expr> numerator, List<Expr> denominator) {

        public boolean isFraction() {
            return !denominator.isEmpty();
        }
    }

    /**
     * Split the factors of a {@code Times} into sign, numerator and denominator.
     *
     * <p>Three things become a denominator: a {@code Power(b, -1)}, and the denominator of a rational
     * coefficient. The latter matters more than it looks — {@code Sum(1/n^2, …)} comes back as
     * {@code Times(1/6, Power(Pi, 2))}, and nobody writes that as {@code 1/6*pi^2}.
     */
    public static Product splitProduct(List<Expr> factors) {
        List<Expr> numerator = new ArrayList<>();
        List<Expr> denominator = new ArrayList<>();
        boolean negative = false;
        for (Expr factor : factors) {
            if (isMinusOne(factor)) {
                negative = !negative;
                continue;
            }
            Expr reciprocal = reciprocalBase(factor);
            if (reciprocal != null) {
                denominator.add(reciprocal);
                continue;
            }
            if (factor instanceof Rat r) {
                if (r.num().signum() < 0) {
                    negative = !negative;
                }
                BigInteger magnitude = r.num().abs();
                if (!magnitude.equals(BigInteger.ONE)) {
                    numerator.add(Exprs.of(magnitude));
                }
                denominator.add(Exprs.of(r.den()));
                continue;
            }
            numerator.add(factor);
        }
        return new Product(negative, List.copyOf(numerator), List.copyOf(denominator));
    }

    /**
     * The positive part of a negated term, or null when it is not negated.
     *
     * <p>The coefficient is ANY negative literal, not just −1. The engine returns {@code -ln(u)/6} as
     * {@code Times(-1/6, Log(u))}, and matching only −1 leaves it printing as {@code a + -ln(u)/6} — a
     * stray sign that reads as a bug in the algebra rather than in the rendering.
     */
    public static Expr negatedPart(Expr e) {
        if (e instanceof Call c
                && "Times".equals(c.head())
                && c.arity() >= 2
                && c.arg(0) instanceof Num n
                && isNegative(n)) {
            List<Expr> rest = new ArrayList<>(c.args());
            Expr positive = negate(n);
            if (isOne(positive)) {
                rest.remove(0); // a bare -1 contributes nothing once the sign is out
            } else {
                rest.set(0, positive);
            }
            return rest.size() == 1 ? rest.get(0) : Exprs.call("Times", rest);
        }
        if (e instanceof Num n && isNegative(n)) {
            return negate(n);
        }
        return null;
    }

    /** The {@code b} of {@code Power(b, -1)} — the engine's spelling of a reciprocal — else null. */
    public static Expr reciprocalBase(Expr e) {
        return e instanceof Call c && "Power".equals(c.head()) && c.arity() == 2 && isMinusOne(c.arg(1))
                ? c.arg(0)
                : null;
    }

    /**
     * One half, however it was spelled.
     *
     * <p>Both forms occur: the ENGINE returns an exact {@code Rat(1,2)}, while the PARSER gives
     * {@code Divide(1, 2)} for a typed {@code x^(1/2)} because nothing has evaluated it yet. Matching
     * one draws a radical for engine output and a raised fraction for the same thing typed by hand.
     */
    public static boolean isHalf(Expr e) {
        if (e instanceof Rat r) {
            return r.num().equals(BigInteger.ONE) && r.den().equals(TWO);
        }
        return e instanceof Call c
                && "Divide".equals(c.head())
                && c.arity() == 2
                && c.arg(0) instanceof Int n
                && n.value().equals(BigInteger.ONE)
                && c.arg(1) instanceof Int d
                && d.value().equals(TWO);
    }

    public static boolean isMinusOne(Expr e) {
        return e instanceof Int i && i.value().equals(MINUS_ONE);
    }

    public static boolean isOne(Expr e) {
        return e instanceof Int i && i.value().equals(BigInteger.ONE);
    }

    public static boolean isNegative(Num n) {
        return switch (n) {
            case Int i -> i.value().signum() < 0;
            case Rat r -> r.num().signum() < 0;
            case Flt f -> f.value().signum() < 0;
        };
    }

    public static Expr negate(Num n) {
        return switch (n) {
            case Int i -> Exprs.of(i.value().negate());
            case Rat r -> Exprs.rat(r.num().negate(), r.den());
            case Flt f -> Exprs.of(f.value().negate());
        };
    }
}
