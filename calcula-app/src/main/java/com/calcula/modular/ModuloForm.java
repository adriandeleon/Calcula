package com.calcula.modular;

import java.math.BigInteger;

/**
 * A number in a ring: {@code 5 mod 7}.
 *
 * <p>The point is that arithmetic <b>stays</b> in the ring. {@code (5 mod 7) + (4 mod 7)} is
 * {@code 2 mod 7} rather than nine, and every operation reduces on the way out, so a long calculation
 * never leaves the small numbers it started in.
 *
 * <p>Distinct from the {@code mod} <em>function</em>, which takes a remainder once and hands back an
 * ordinary integer. This is a value that remembers what ring it is in, and that is the whole
 * difference — {@code mod(5, 7) + mod(4, 7)} is nine, and correct.
 *
 * <p>The representative is always in {@code [0, m)}, so two forms in the same ring are equal exactly
 * when they represent the same thing. Java's {@code %} keeps the sign of the dividend, which would
 * make {@code -1 mod 7} and {@code 6 mod 7} different values meaning the same number.
 */
public record ModuloForm(BigInteger value, BigInteger modulus) {

    public ModuloForm {
        if (value == null || modulus == null) {
            throw new IllegalArgumentException("a modulo form needs both halves");
        }
        if (modulus.signum() <= 0) {
            throw new ArithmeticException("a modulus has to be positive");
        }
        value = value.mod(modulus);
    }

    public static ModuloForm of(long value, long modulus) {
        return new ModuloForm(BigInteger.valueOf(value), BigInteger.valueOf(modulus));
    }

    public ModuloForm add(ModuloForm other) {
        return new ModuloForm(value.add(sameRing(other)), modulus);
    }

    public ModuloForm subtract(ModuloForm other) {
        return new ModuloForm(value.subtract(sameRing(other)), modulus);
    }

    public ModuloForm multiply(ModuloForm other) {
        return new ModuloForm(value.multiply(sameRing(other)), modulus);
    }

    /**
     * Division, which is multiplication by the inverse and does not always exist.
     *
     * <p>{@code 1/2 mod 4} has no answer, because two has no inverse where four is the modulus — and
     * saying so is much better than the plausible wrong number a floor division would produce.
     */
    public ModuloForm divide(ModuloForm other) {
        BigInteger divisor = sameRing(other);
        if (!divisor.gcd(modulus).equals(BigInteger.ONE)) {
            throw new ArithmeticException(divisor + " has no inverse modulo " + modulus);
        }
        return new ModuloForm(value.multiply(divisor.modInverse(modulus)), modulus);
    }

    public ModuloForm negate() {
        return new ModuloForm(value.negate(), modulus);
    }

    /**
     * A power, by repeated squaring rather than by computing the power and reducing.
     *
     * <p>Which is the reason a ring is worth having: {@code (7 mod 13)^1000000} is instant here and
     * is a number with 845,000 digits the other way round.
     */
    public ModuloForm power(BigInteger exponent) {
        if (exponent.signum() < 0) {
            if (!value.gcd(modulus).equals(BigInteger.ONE)) {
                throw new ArithmeticException(value + " has no inverse modulo " + modulus);
            }
            return new ModuloForm(value.modPow(exponent.negate(), modulus).modInverse(modulus), modulus);
        }
        return new ModuloForm(value.modPow(exponent, modulus), modulus);
    }

    /** The other side's value, once it is established that both are in the same ring. */
    private BigInteger sameRing(ModuloForm other) {
        if (!modulus.equals(other.modulus)) {
            throw new ArithmeticException("those are in different rings: " + modulus + " and " + other.modulus);
        }
        return other.value;
    }
}
