package com.calcula.error;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * A measurement and how far out it might be: {@code 2 +/- 0.1}.
 *
 * <p><b>Not a new kind of number</b>, and the issue that asked for this said it would have to be. It
 * does not: this tree has exactly three leaves and everything structured is a call, so an error form
 * is {@code PlusMinus(value, error)} like every other structure here. Widening {@link
 * com.calcula.expr.Expr.Num} would have meant touching arithmetic, formatting, layout and the round
 * trip for a thing that is a <em>pair</em> rather than a number — the cheaper design was the one the
 * codebase already argues for.
 *
 * <p>Errors combine in <b>quadrature</b>, which assumes the two measurements are independent. That is
 * the assumption every laboratory makes and it is worth stating, because it is why {@code x - x} is
 * not zero-error: subtracting a measurement from itself gives {@code 0 +/- 0.14} when the error was
 * 0.1, and a calculator that answered {@code 0 +/- 0} would be claiming a correlation nobody told it
 * about.
 *
 * <p>The error is kept non-negative. A negative uncertainty is not a smaller one; it is a sign error
 * somewhere upstream, and carrying it would quietly cancel a real error later.
 */
public record ErrorForm(BigDecimal value, BigDecimal error) {

    public ErrorForm {
        if (value == null || error == null) {
            throw new IllegalArgumentException("an error form needs both halves");
        }
        error = error.abs();
    }

    /** An exact number, which is the same thing with no error at all. */
    public static ErrorForm exact(BigDecimal value) {
        return new ErrorForm(value, BigDecimal.ZERO);
    }

    public ErrorForm add(ErrorForm other, MathContext mc) {
        return new ErrorForm(value.add(other.value, mc), quadrature(error, other.error, mc));
    }

    public ErrorForm subtract(ErrorForm other, MathContext mc) {
        return new ErrorForm(value.subtract(other.value, mc), quadrature(error, other.error, mc));
    }

    /**
     * Multiplication combines <em>relative</em> errors in quadrature.
     *
     * <p>Which is why a zero value is handled apart: the relative error of zero is undefined, and the
     * limit of the formula there is the other factor's error scaled by this value — exactly what
     * falls out of doing it in absolute terms instead.
     */
    public ErrorForm multiply(ErrorForm other, MathContext mc) {
        BigDecimal product = value.multiply(other.value, mc);
        BigDecimal fromThis = error.multiply(other.value, mc);
        BigDecimal fromOther = other.error.multiply(value, mc);
        return new ErrorForm(product, quadrature(fromThis, fromOther, mc));
    }

    public ErrorForm divide(ErrorForm other, MathContext mc) {
        if (other.value.signum() == 0) {
            throw new ArithmeticException("division by zero");
        }
        BigDecimal quotient = value.divide(other.value, mc);
        BigDecimal fromThis = error.divide(other.value, mc);
        BigDecimal fromOther = other.error.multiply(value, mc).divide(other.value.multiply(other.value, mc), mc);
        return new ErrorForm(quotient, quadrature(fromThis, fromOther, mc));
    }

    /** Negation moves the value and leaves the uncertainty exactly where it was. */
    public ErrorForm negate() {
        return new ErrorForm(value.negate(), error);
    }

    /**
     * A power, for a whole exponent.
     *
     * <p>{@code (x ± dx)^n} has relative error {@code |n|} times that of {@code x}, because it is
     * <em>one</em> measurement raised — not {@code n} independent ones multiplied together.
     *
     * <p><b>So {@code x^2} and {@code x*x} give different errors, and both are right.</b> Squaring
     * 2 ± 0.1 gives 4 ± 0.4; multiplying it by itself gives 4 ± 0.28, because a product of two things
     * is combined in quadrature and quadrature assumes independence. It is the same distinction as
     * {@code x - x} not being zero-error, and a calculator that quietly made them agree would be
     * inventing a correlation in one direction or losing one in the other.
     *
     * <p>Whole exponents only: a fractional power of a measurement that might be negative is a
     * question this cannot answer.
     */
    public ErrorForm power(int exponent, MathContext mc) {
        if (exponent == 0) {
            return exact(BigDecimal.ONE);
        }
        BigDecimal raised = value.pow(Math.abs(exponent), mc);
        BigDecimal result = exponent > 0 ? raised : BigDecimal.ONE.divide(raised, mc);
        if (value.signum() == 0) {
            // The derivative of x^n at zero is dx for n = 1 and zero above it.
            return new ErrorForm(result, Math.abs(exponent) == 1 ? error : BigDecimal.ZERO);
        }
        BigDecimal relative = error.divide(value.abs(), mc);
        BigDecimal scaled = relative.multiply(new BigDecimal(Math.abs(exponent)), mc);
        return new ErrorForm(result, result.abs().multiply(scaled, mc));
    }

    /** True when there is no uncertainty left, and the form may as well be a plain number. */
    public boolean isExact() {
        return error.signum() == 0;
    }

    /** {@code sqrt(a^2 + b^2)}, in decimal, at the caller's precision. */
    private static BigDecimal quadrature(BigDecimal a, BigDecimal b, MathContext mc) {
        BigDecimal squares = a.multiply(a, mc).add(b.multiply(b, mc), mc);
        return squares.sqrt(mc);
    }
}
