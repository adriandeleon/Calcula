package com.calcula.expr;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

import com.calcula.expr.Expr.Flt;
import com.calcula.expr.Expr.Int;
import com.calcula.expr.Expr.Num;
import com.calcula.expr.Expr.Rat;

/**
 * Exact arithmetic on the numeric leaves, with the coercion lattice {@code Int → Rat → Flt}.
 *
 * <p>This exists so ordinary sums do not pay a CAS round trip. Evaluating {@code 1/2 + 1/3} through the
 * engine measured 0.38 ms; here it is a gcd. The engine is for algebra, not for adding two numbers.
 *
 * <p>Exactness is preserved wherever it can be: {@code Int op Int} stays an {@link Int} (except
 * division, which may produce a {@link Rat}), and a {@link Flt} anywhere in an expression makes the
 * whole result inexact — the usual contagion rule, and the reason {@code Flt} never appears by accident.
 */
public final class Arith {

    /** Emacs Calc's default working precision. */
    public static final MathContext DEFAULT_PRECISION = new MathContext(12);

    /**
     * Ceiling on the size of an exact power, in bits. {@code 9^9^9} is a perfectly reasonable thing to
     * type by accident, and without a guard it is an out-of-memory error several seconds later rather
     * than a message. A million bits is ~300,000 digits, well past anything a calculator should print.
     */
    private static final long MAX_RESULT_BITS = 1_000_000L;

    private Arith() {}

    /** Thrown when a result cannot be produced exactly and the caller should defer to the CAS. */
    public static final class NotExact extends ArithmeticException {
        private static final long serialVersionUID = 1L;

        public NotExact(String message) {
            super(message);
        }
    }

    // ------------------------------------------------------------------ operations

    public static Num add(Num a, Num b) {
        return add(a, b, DEFAULT_PRECISION);
    }

    public static Num add(Num a, Num b, MathContext mc) {
        if (a instanceof Flt || b instanceof Flt) {
            return new Flt(dec(a, mc).add(dec(b, mc), mc));
        }
        // p/q + r/s = (ps + rq) / qs
        BigInteger p = num(a);
        BigInteger q = den(a);
        BigInteger r = num(b);
        BigInteger s = den(b);
        return Exprs.rat(p.multiply(s).add(r.multiply(q)), q.multiply(s));
    }

    public static Num sub(Num a, Num b) {
        return sub(a, b, DEFAULT_PRECISION);
    }

    public static Num sub(Num a, Num b, MathContext mc) {
        return add(a, neg(b), mc);
    }

    public static Num mul(Num a, Num b) {
        return mul(a, b, DEFAULT_PRECISION);
    }

    public static Num mul(Num a, Num b, MathContext mc) {
        if (a instanceof Flt || b instanceof Flt) {
            return new Flt(dec(a, mc).multiply(dec(b, mc), mc));
        }
        return Exprs.rat(num(a).multiply(num(b)), den(a).multiply(den(b)));
    }

    public static Num div(Num a, Num b) {
        return div(a, b, DEFAULT_PRECISION);
    }

    public static Num div(Num a, Num b, MathContext mc) {
        if (isZero(b)) {
            throw new ArithmeticException("division by zero");
        }
        if (a instanceof Flt || b instanceof Flt) {
            return new Flt(dec(a, mc).divide(dec(b, mc), mc));
        }
        return Exprs.rat(num(a).multiply(den(b)), den(a).multiply(num(b)));
    }

    public static Num neg(Num a) {
        return switch (a) {
            case Int i -> new Int(i.value().negate());
            case Rat r -> new Rat(r.num().negate(), r.den());
            case Flt f -> new Flt(f.value().negate());
        };
    }

    /**
     * Exact powers only. A non-integer exponent, or an inexact base with an inexact exponent, throws
     * {@link NotExact} so the caller can hand the whole expression to the CAS — which is the right place
     * for {@code 2^(1/2)} to become {@code Sqrt(2)} rather than a rounded decimal.
     */
    public static Num pow(Num base, Num exponent) {
        return pow(base, exponent, DEFAULT_PRECISION);
    }

    public static Num pow(Num base, Num exponent, MathContext mc) {
        if (!(exponent instanceof Int e)) {
            throw new NotExact("non-integer exponent");
        }
        BigInteger exp = e.value();
        if (exp.bitLength() > 31) {
            throw new NotExact("exponent too large");
        }
        int n = exp.intValueExact();
        if (n == 0) {
            if (isZero(base)) {
                throw new ArithmeticException("0^0 is undefined");
            }
            return Exprs.ONE;
        }
        if (base instanceof Flt f) {
            if (n < 0 && f.value().signum() == 0) {
                throw new ArithmeticException("division by zero");
            }
            // BigDecimal.pow rejects a negative exponent outright, so invert explicitly.
            BigDecimal magnitude = f.value().pow(Math.abs(n), mc);
            return new Flt(n > 0 ? magnitude : BigDecimal.ONE.divide(magnitude, mc));
        }
        if (n < 0 && isZero(base)) {
            throw new ArithmeticException("division by zero");
        }
        BigInteger p = num(base);
        BigInteger q = den(base);
        checkPowSize(p.max(q), Math.abs(n));
        BigInteger pn = p.pow(Math.abs(n));
        BigInteger qn = q.pow(Math.abs(n));
        return n > 0 ? Exprs.rat(pn, qn) : Exprs.rat(qn, pn);
    }

    /** Sign of a number: -1, 0 or 1. */
    public static int signum(Num a) {
        return switch (a) {
            case Int i -> i.value().signum();
            case Rat r -> r.num().signum();
            case Flt f -> f.value().signum();
        };
    }

    public static boolean isZero(Num a) {
        return signum(a) == 0;
    }

    /** Compares by value across kinds, so {@code Int(2)} and {@code Flt(2.0)} compare equal. */
    public static int compare(Num a, Num b) {
        if (a instanceof Flt || b instanceof Flt) {
            return dec(a, DEFAULT_PRECISION).compareTo(dec(b, DEFAULT_PRECISION));
        }
        // p/q ? r/s  ⇔  ps ? rq, denominators being positive by construction
        return num(a).multiply(den(b)).compareTo(num(b).multiply(den(a)));
    }

    // ------------------------------------------------------------------ internals

    private static void checkPowSize(BigInteger base, int exp) {
        long bits = (long) base.bitLength() * exp;
        if (bits > MAX_RESULT_BITS) {
            throw new NotExact("result would be about " + bits + " bits — too large to compute exactly");
        }
    }

    private static BigInteger num(Num a) {
        return switch (a) {
            case Int i -> i.value();
            case Rat r -> r.num();
            case Flt f -> throw new IllegalArgumentException("inexact value has no exact numerator");
        };
    }

    private static BigInteger den(Num a) {
        return switch (a) {
            case Int i -> BigInteger.ONE;
            case Rat r -> r.den();
            case Flt f -> throw new IllegalArgumentException("inexact value has no exact denominator");
        };
    }

    /**
     * A number as a decimal, rounded to {@code mc}.
     *
     * <p>Public because fraction mode needs it: switching exact fractions off turns every rational in
     * an answer into a decimal, and that conversion has to round the same way the rest of the
     * arithmetic does rather than inventing its own rule.
     */
    public static BigDecimal toDecimal(Num a, MathContext mc) {
        return dec(a, mc);
    }

    private static BigDecimal dec(Num a, MathContext mc) {
        return switch (a) {
            case Int i -> new BigDecimal(i.value());
            case Rat r -> new BigDecimal(r.num()).divide(new BigDecimal(r.den()), mc);
            case Flt f -> f.value();
        };
    }
}
