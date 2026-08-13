package com.calcula.ui;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Set;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import com.calcula.parse.Formatter;

/**
 * Which stack values are worth showing a decimal for, and which are not.
 *
 * <p>A CAS spends its life moving between exact and approximate, and the mode line reports the
 * <b>policy</b> while the gutter rail reports the <b>value</b>. Neither answers the question actually
 * being asked most of the time, which is <em>yes, but how big is it</em>. {@code Pi^2/6} is the right
 * answer and {@code 1.644934066848} is the one you can compare against something.
 *
 * <p>Pure, and the deciding is the point: asking the engine costs a round trip on a thread the
 * calculator itself needs, so the cheap structural question is asked first and most values never
 * reach the engine at all.
 */
public final class Approximation {

    /**
     * Symbols that stand for a number.
     *
     * <p>Everything else is a free variable, and {@code x + 1} has no decimal — asking for one gets
     * {@code 1 + x} back, which is a round trip spent to learn nothing.
     */
    private static final Set<String> NUMERIC_CONSTANTS = Set.of("Pi", "E");

    private Approximation() {}

    /**
     * True when a decimal would say something the value does not already say.
     *
     * <p>Three refusals, each for its own reason. An {@code Int} <em>is</em> its decimal. Anything
     * already carrying floating-point error has nothing to add — and it wears the amber rail, which is
     * the honest thing to show there instead. Anything with a free symbol in it has no decimal at all.
     *
     * <p>A list is refused as a matter of taste rather than of arithmetic: every element would need
     * one, and a margin repeating a whole vector in decimal is a second stack rather than an
     * annotation.
     */
    public static boolean worth(Expr value) {
        if (value == null || value instanceof Expr.Int || Exprs.isList(value)) {
            return false;
        }
        return !Exprs.containsInexact(value) && closedNumeric(value);
    }

    /** Every leaf is a number or a named constant, so the whole tree has a value. */
    private static boolean closedNumeric(Expr e) {
        return switch (e) {
            case null -> false;
            case Expr.Int ignored -> true;
            case Expr.Rat ignored -> true;
            case Expr.Flt ignored -> true;
            case Expr.Sym s -> NUMERIC_CONSTANTS.contains(s.name());
            case Expr.Call c -> !Exprs.isList(c) && c.args().stream().allMatch(Approximation::closedNumeric);
            default -> false;
        };
    }

    /**
     * The decimal, worked out here, or null when this value needs the engine.
     *
     * <p>A ratio is the overwhelmingly common case and needs no CAS at all — which matters, because
     * "the CAS is a capability, not a precondition" and exact arithmetic already works without one.
     * A window with no engine still adds up fractions, so it should still be able to say how big the
     * answer is.
     *
     * <p>Everything else — {@code Pi^2/6}, {@code sqrt(2)} — is a closed form the engine has to
     * reduce, and goes the long way round.
     */
    public static String direct(Expr value, int precision) {
        if (!(value instanceof Expr.Rat r)) {
            return null;
        }
        BigDecimal quotient = new BigDecimal(r.num())
                .divide(new BigDecimal(r.den()), new MathContext(Math.max(1, precision), RoundingMode.HALF_UP));
        // 1/2 is 0.5 and not 0.500000000000: the precision is a ceiling on what can be known, not a
        // demand that every digit of it be shown.
        return "≈ " + quotient.stripTrailingZeros().toPlainString();
    }

    /**
     * What the engine should be asked, or null when it should not be asked at all.
     *
     * <p>Built here rather than at the call site so the decision and the request cannot disagree —
     * the version that asks for something {@link #worth} said no to is the expensive bug.
     */
    public static Expr request(Expr value, int precision) {
        return worth(value) ? Exprs.call("N", value, Exprs.of(precision)) : null;
    }

    /**
     * The answer, as it should read in the margin, or null when it is not worth showing.
     *
     * <p>An engine that hands back something that is still not a number has told us the value has no
     * decimal after all — {@code N} is total, so it answers rather than failing — and the margin says
     * nothing rather than repeating the value beside itself.
     */
    public static String shown(Expr answer) {
        if (!(answer instanceof Expr.Num) || answer instanceof Expr.Int) {
            return null;
        }
        return "≈ " + Formatter.format(answer);
    }
}
