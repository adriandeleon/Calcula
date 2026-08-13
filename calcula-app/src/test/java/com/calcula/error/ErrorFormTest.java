package com.calcula.error;

import java.math.BigDecimal;
import java.math.MathContext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorFormTest {

    private static final MathContext MC = new MathContext(12);

    private static ErrorForm of(String value, String error) {
        return new ErrorForm(new BigDecimal(value), new BigDecimal(error));
    }

    /** Six significant figures, trailing zeros dropped — comparing scale here would test BigDecimal. */
    private static String shown(ErrorForm form) {
        return plain(form.value()) + " +/- " + plain(form.error());
    }

    private static String plain(BigDecimal value) {
        BigDecimal rounded = value.round(new MathContext(6)).stripTrailingZeros();
        return rounded.scale() < 0 ? rounded.setScale(0).toPlainString() : rounded.toPlainString();
    }

    @Test
    void addingCombinesTheErrorsInQuadrature() {
        // sqrt(0.1^2 + 0.2^2) = 0.223607, not 0.3. The errors are assumed independent, so they do not
        // simply add — that is the whole content of an error form.
        assertEquals("5 +/- 0.223607", shown(of("2", "0.1").add(of("3", "0.2"), MC)));
    }

    @Test
    void subtractingDoesTheSameThingToTheError() {
        // An error never shrinks because a value did. Subtracting is as uncertain as adding.
        assertEquals("-1 +/- 0.223607", shown(of("2", "0.1").subtract(of("3", "0.2"), MC)));
    }

    @Test
    void aMeasurementMinusItselfIsNotZeroError() {
        // The independence assumption, made visible. Answering 0 +/- 0 would be claiming a
        // correlation nobody told the calculator about.
        ErrorForm x = of("2", "0.1");
        assertEquals("0 +/- 0.141421", shown(x.subtract(x, MC)));
    }

    @Test
    void multiplyingCombinesRelativeErrors() {
        // 2 ± 0.1 times 3 ± 0.2: value 6, error sqrt((0.1*3)^2 + (0.2*2)^2) = 0.5.
        assertEquals("6 +/- 0.5", shown(of("2", "0.1").multiply(of("3", "0.2"), MC)));
    }

    @Test
    void aZeroValueStillMultiplies() {
        // The relative error of zero is undefined; doing it in absolute terms has a limit there and
        // this is it.
        assertEquals("0 +/- 0.3", shown(of("0", "0.1").multiply(of("3", "0"), MC)));
    }

    @Test
    void dividing() {
        // Relative errors 0.05 and 0.0667 in quadrature is 0.0833, and the value is 2.
        assertEquals("2 +/- 0.166667", shown(of("6", "0.3").divide(of("3", "0.2"), MC)));
    }

    @Test
    void dividingByZeroIsRefused() {
        assertThrows(ArithmeticException.class, () -> of("1", "0.1").divide(of("0", "0.1"), MC));
    }

    @Test
    void negatingLeavesTheUncertaintyWhereItWas() {
        assertEquals("-2 +/- 0.1", shown(of("2", "0.1").negate()));
    }

    @Test
    void anErrorIsNeverNegative() {
        // A negative uncertainty is not a smaller one; it is a sign error upstream, and carrying it
        // would quietly cancel a real error later.
        assertTrue(of("2", "-0.1").error().signum() > 0);
    }

    @Test
    void aWholePower() {
        // (2 ± 0.1)^2 = 4 ± 0.4: the relative error doubles.
        assertEquals("4 +/- 0.4", shown(of("2", "0.1").power(2, MC)));
        assertEquals("1 +/- 0", shown(of("2", "0.1").power(0, MC)), "anything to the zero is exactly one");
        assertEquals("0.25 +/- 0.025", shown(of("2", "0.1").power(-2, MC)), "and a negative exponent");
    }

    @Test
    void squaringAndMultiplyingByItselfDisagreeOnPurpose() {
        // x^2 is one measurement raised; x*x is a product of two, and a product combines in
        // quadrature because quadrature assumes independence. Making them agree would invent a
        // correlation in one direction or lose one in the other.
        ErrorForm x = of("2", "0.1");
        assertEquals("4 +/- 0.4", shown(x.power(2, MC)));
        assertEquals("4 +/- 0.282843", shown(x.multiply(x, MC)));
    }

    @Test
    void anExactFormIsJustANumber() {
        assertTrue(ErrorForm.exact(BigDecimal.ONE).isExact());
        assertTrue(!of("2", "0.1").isExact());
    }
}
