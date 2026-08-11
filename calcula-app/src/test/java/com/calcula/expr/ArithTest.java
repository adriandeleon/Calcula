package com.calcula.expr;

import java.math.BigDecimal;
import java.math.BigInteger;

import com.calcula.expr.Expr.Flt;
import com.calcula.expr.Expr.Int;
import com.calcula.expr.Expr.Num;
import com.calcula.expr.Expr.Rat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArithTest {

    @Test
    void integersStayIntegers() {
        assertEquals(Exprs.of(5), Arith.add(Exprs.of(2), Exprs.of(3)));
        assertEquals(Exprs.of(6), Arith.mul(Exprs.of(2), Exprs.of(3)));
        assertEquals(Exprs.of(-1), Arith.sub(Exprs.of(2), Exprs.of(3)));
    }

    @Test
    void divisionOfIntegersProducesAnExactRational() {
        // The behaviour that separates this from a pocket calculator: 1/3 is a value, not 0.333…
        assertEquals(Exprs.rat(1, 3), Arith.div(Exprs.of(1), Exprs.of(3)));
        assertEquals(Exprs.of(2), Arith.div(Exprs.of(6), Exprs.of(3)), "should collapse to an Int");
    }

    @Test
    void rationalsAreAlwaysReducedAndSignedInTheNumerator() {
        Num half = Exprs.rat(2, 4);
        assertEquals(Exprs.rat(1, 2), half);
        assertEquals(new Rat(BigInteger.ONE, BigInteger.TWO), half);

        Num negative = Exprs.rat(1, -2);
        assertEquals(Exprs.rat(-1, 2), negative);
        assertEquals(BigInteger.valueOf(2), ((Rat) negative).den(), "denominator must stay positive");
    }

    @Test
    void aWholeFractionCollapsesToAnIntegerSoEqualityMeansSomething() {
        // Rat(4,2) and Int(2) would be unequal values for the same number; the factory prevents it.
        assertEquals(Exprs.of(2), Exprs.rat(4, 2));
        assertInstanceOf(Int.class, Exprs.rat(4, 2));
        assertThrows(IllegalArgumentException.class, () -> new Rat(BigInteger.TWO, BigInteger.ONE));
    }

    @Test
    void theClassicExactSum() {
        assertEquals(Exprs.rat(5, 6), Arith.add(Exprs.rat(1, 2), Exprs.rat(1, 3)));
    }

    @Test
    void inexactnessIsContagious() {
        Num result = Arith.add(Exprs.of(1), Exprs.of(new BigDecimal("0.5")));
        assertInstanceOf(Flt.class, result);
        assertEquals(0, ((Flt) result).value().compareTo(new BigDecimal("1.5")));
    }

    @Test
    void divisionByZeroIsReportedRatherThanProducingInfinity() {
        assertThrows(ArithmeticException.class, () -> Arith.div(Exprs.of(1), Exprs.of(0)));
        assertThrows(ArithmeticException.class, () -> Arith.div(Exprs.of(1), Exprs.of(0.0)));
    }

    @Test
    void integerPowersAreExactInBothDirections() {
        assertEquals(Exprs.of(8), Arith.pow(Exprs.of(2), Exprs.of(3)));
        assertEquals(Exprs.rat(1, 8), Arith.pow(Exprs.of(2), Exprs.of(-3)));
        assertEquals(Exprs.rat(4, 9), Arith.pow(Exprs.rat(2, 3), Exprs.of(2)));
        assertEquals(Exprs.ONE, Arith.pow(Exprs.of(7), Exprs.of(0)));
    }

    @Test
    void aNonIntegerExponentDefersToTheCasInsteadOfRounding() {
        // 2^(1/2) must become Sqrt(2) in the engine, not 1.41… here.
        assertThrows(Arith.NotExact.class, () -> Arith.pow(Exprs.of(2), Exprs.rat(1, 2)));
    }

    @Test
    void anAbsurdPowerIsRefusedQuicklyRatherThanExhaustingMemory() {
        // 9^(9^9) is easy to type by accident. Without the guard this is an OOM some seconds later.
        Arith.NotExact e = assertThrows(Arith.NotExact.class, () -> Arith.pow(Exprs.of(9), Exprs.of(387_420_489L)));
        assertTrue(e.getMessage().contains("too large"), e.getMessage());
    }

    @Test
    void zeroToTheZeroIsUndefinedRatherThanOne() {
        assertThrows(ArithmeticException.class, () -> Arith.pow(Exprs.of(0), Exprs.of(0)));
    }

    @Test
    void comparisonWorksAcrossKinds() {
        assertEquals(0, Arith.compare(Exprs.of(2), Exprs.of(2.0)));
        assertTrue(Arith.compare(Exprs.rat(1, 3), Exprs.rat(1, 2)) < 0);
        assertTrue(Arith.compare(Exprs.of(1.5), Exprs.rat(1, 2)) > 0);
    }

    @Test
    void signAndZeroDetection() {
        assertEquals(0, Arith.signum(Exprs.ZERO));
        assertEquals(-1, Arith.signum(Exprs.rat(-1, 3)));
        assertTrue(Arith.isZero(Exprs.of(0.0)));
    }
}
