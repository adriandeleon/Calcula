package com.calcula.expr;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * The expression tree. Immutable, and deliberately shaped like Symja's own.
 *
 * <p>There are exactly three kinds of thing: a number, a symbol, and a call. Everything structured is a
 * call — a list is {@code Call("List", …)}, a sum is {@code Call("Plus", …)}, an equation is {@code
 * Call("Equal", …)}. That is not laziness; it is what makes conversion to and from the engine
 * <em>total</em>. A head we have never heard of round-trips as a generic {@link Call} instead of
 * throwing, so an engine upgrade that introduces new functions cannot break the adapter, and swapping
 * the engine later stays a real option rather than a stated intention.
 *
 * <p>Numbers are exact by default. {@link Int} and {@link Rat} carry no error; {@link Flt} is the only
 * inexact kind and only appears when the user asks for it or an operation cannot stay exact.
 */
public sealed interface Expr {

    /** Marker for the three numeric leaves. */
    sealed interface Num extends Expr permits Int, Rat, Flt {}

    /** An arbitrary-precision integer. */
    record Int(BigInteger value) implements Num {
        public Int {
            if (value == null) {
                throw new IllegalArgumentException("null integer");
            }
        }
    }

    /**
     * An exact rational, always in canonical form: {@code den > 1} and {@code gcd(num, den) == 1}.
     *
     * <p>The constructor reduces and then REFUSES a denominator of 1, because {@code Rat(2,1)} and
     * {@code Int(2)} would be unequal values meaning the same number — which quietly breaks every
     * equality check downstream. Build rationals with {@link Exprs#rat} instead; it returns an {@link
     * Int} when the fraction is whole.
     */
    record Rat(BigInteger num, BigInteger den) implements Num {
        public Rat {
            if (num == null || den == null) {
                throw new IllegalArgumentException("null rational part");
            }
            if (den.signum() == 0) {
                throw new ArithmeticException("division by zero");
            }
            if (den.signum() < 0) { // keep the sign in the numerator
                num = num.negate();
                den = den.negate();
            }
            BigInteger g = num.gcd(den);
            if (!g.equals(BigInteger.ONE)) {
                num = num.divide(g);
                den = den.divide(g);
            }
            if (den.equals(BigInteger.ONE)) {
                throw new IllegalArgumentException("whole number — use Exprs.rat, which returns an Int");
            }
        }
    }

    /** An inexact decimal. Carries its own scale; precision policy lives in {@link Arith}. */
    record Flt(BigDecimal value) implements Num {
        public Flt {
            if (value == null) {
                throw new IllegalArgumentException("null decimal");
            }
        }
    }

    /** A variable or named constant: {@code x}, {@code Pi}, {@code E}. Names follow the engine's. */
    record Sym(String name) implements Expr {
        public Sym {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("blank symbol name");
            }
        }
    }

    /** A function application: a head and its arguments, e.g. {@code Call("Sin", [x])}. */
    record Call(String head, List<Expr> args) implements Expr {
        public Call {
            if (head == null || head.isBlank()) {
                throw new IllegalArgumentException("blank call head");
            }
            args = List.copyOf(args); // defensive + immutable
        }

        public int arity() {
            return args.size();
        }

        public Expr arg(int i) {
            return args.get(i);
        }
    }
}
