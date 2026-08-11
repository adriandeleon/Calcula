package com.calcula.expr;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import com.calcula.expr.Expr.Call;
import com.calcula.expr.Expr.Flt;
import com.calcula.expr.Expr.Int;
import com.calcula.expr.Expr.Num;
import com.calcula.expr.Expr.Rat;
import com.calcula.expr.Expr.Sym;

/** Factories, constants and predicates for {@link Expr}. The only sanctioned way to build a rational. */
public final class Exprs {

    // Symbol names match the engine's, so the adapter is a rename and never a translation table.
    public static final Sym PI = new Sym("Pi");
    public static final Sym E = new Sym("E");
    public static final Sym I = new Sym("I");
    public static final Sym INFINITY = new Sym("Infinity");

    public static final Int ZERO = new Int(BigInteger.ZERO);
    public static final Int ONE = new Int(BigInteger.ONE);
    public static final Int MINUS_ONE = new Int(BigInteger.valueOf(-1));

    /** The head every list-shaped value uses, matching the engine. */
    public static final String LIST = "List";

    private Exprs() {}

    // ------------------------------------------------------------------ numbers

    public static Int of(long value) {
        return new Int(BigInteger.valueOf(value));
    }

    public static Int of(BigInteger value) {
        return new Int(value);
    }

    public static Flt of(BigDecimal value) {
        return new Flt(value);
    }

    public static Flt of(double value) {
        return new Flt(BigDecimal.valueOf(value));
    }

    /**
     * An exact fraction, reduced — returning an {@link Int} when it comes out whole, so that {@code
     * rat(4,2)} and {@code of(2)} are the same value.
     */
    public static Num rat(BigInteger num, BigInteger den) {
        if (den.signum() == 0) {
            throw new ArithmeticException("division by zero");
        }
        if (num.signum() == 0) {
            return ZERO;
        }
        if (den.signum() < 0) {
            num = num.negate();
            den = den.negate();
        }
        BigInteger g = num.gcd(den);
        num = num.divide(g);
        den = den.divide(g);
        return den.equals(BigInteger.ONE) ? new Int(num) : new Rat(num, den);
    }

    public static Num rat(long num, long den) {
        return rat(BigInteger.valueOf(num), BigInteger.valueOf(den));
    }

    // ------------------------------------------------------------------ structure

    public static Sym sym(String name) {
        return new Sym(name);
    }

    public static Call call(String head, Expr... args) {
        return new Call(head, List.of(args));
    }

    public static Call call(String head, List<Expr> args) {
        return new Call(head, args);
    }

    /** A list value: {@code {1, 2, 3}} in engine terms, {@code [1, 2, 3]} in ours. */
    public static Call list(Expr... items) {
        return new Call(LIST, List.of(items));
    }

    public static Call list(List<Expr> items) {
        return new Call(LIST, items);
    }

    public static boolean isList(Expr e) {
        return e instanceof Call c && LIST.equals(c.head());
    }

    /** The items of a list, or empty for anything else. */
    public static List<Expr> items(Expr e) {
        return isList(e) ? ((Call) e).args() : List.of();
    }

    /** True when every item is itself a list — the shape a matrix has. */
    public static boolean isMatrix(Expr e) {
        List<Expr> rows = items(e);
        return !rows.isEmpty() && rows.stream().allMatch(Exprs::isList);
    }

    public static boolean isNumber(Expr e) {
        return e instanceof Num;
    }

    /** True for an exact number: everything except {@link Flt}. Shallow — see {@link #containsInexact}. */
    public static boolean isExact(Expr e) {
        return e instanceof Int || e instanceof Rat;
    }

    /**
     * True when a {@link Flt} appears anywhere in the tree.
     *
     * <p><b>This is not {@code !isExact(e)}, and the difference is the whole point.</b>
     * {@link #isExact} asks "is this node an exact <em>number</em>", so it answers false for a
     * symbol and false for every {@link Call} — including {@code x + 1}, which carries no error at
     * all. Negating it to mean "inexact" marks every symbolic result as approximate, which is both
     * wrong and the most visible thing in the window.
     *
     * <p>What the UI actually wants is contamination: one {@code Flt} buried anywhere in a sum
     * makes the whole value approximate, and nothing else does. Hence a recursive walk rather than
     * a cast.
     */
    public static boolean containsInexact(Expr e) {
        return switch (e) {
            case null -> false;
            case Flt ignored -> true;
            case Call c -> c.args().stream().anyMatch(Exprs::containsInexact);
            default -> false; // Int, Rat, Sym — exact, or carrying no numeric error
        };
    }

    public static boolean isSymbol(Expr e, String name) {
        return e instanceof Sym s && s.name().equals(name);
    }

    /**
     * A best-effort {@code double}, for plotting and numeric comparison. Never use it for arithmetic
     * that should stay exact — the whole point of {@link Int}/{@link Rat} is not going through here.
     */
    public static double toDouble(Num n) {
        return switch (n) {
            case Int i -> i.value().doubleValue();
            case Rat r ->
                new BigDecimal(r.num())
                        .divide(new BigDecimal(r.den()), java.math.MathContext.DECIMAL64)
                        .doubleValue();
            case Flt f -> f.value().doubleValue();
        };
    }
}
