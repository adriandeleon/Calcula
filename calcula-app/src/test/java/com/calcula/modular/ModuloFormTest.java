package com.calcula.modular;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModuloFormTest {

    @Test
    void arithmeticStaysInTheRing() {
        assertEquals(ModuloForm.of(2, 7), ModuloForm.of(5, 7).add(ModuloForm.of(4, 7)));
        assertEquals(ModuloForm.of(1, 7), ModuloForm.of(5, 7).subtract(ModuloForm.of(4, 7)));
        assertEquals(ModuloForm.of(6, 7), ModuloForm.of(5, 7).multiply(ModuloForm.of(4, 7)));
    }

    @Test
    void theRepresentativeIsAlwaysTheSmallestNonNegativeOne() {
        // Java's % keeps the sign of the dividend, which would make these two different values that
        // mean the same number.
        assertEquals(ModuloForm.of(6, 7), ModuloForm.of(-1, 7));
        assertEquals(ModuloForm.of(1, 7), ModuloForm.of(8, 7));
    }

    @Test
    void dividingIsMultiplyingByTheInverse() {
        // 3 / 2 mod 7: the inverse of 2 is 4, and 3 * 4 = 12 = 5.
        assertEquals(ModuloForm.of(5, 7), ModuloForm.of(3, 7).divide(ModuloForm.of(2, 7)));
    }

    @Test
    void anInverseThatDoesNotExistSaysSo() {
        // Two has no inverse modulo four. A floor division would give a plausible wrong number.
        assertThrows(ArithmeticException.class, () -> ModuloForm.of(1, 4).divide(ModuloForm.of(2, 4)));
    }

    @Test
    void differentRingsDoNotMix() {
        assertThrows(ArithmeticException.class, () -> ModuloForm.of(1, 7).add(ModuloForm.of(1, 5)));
    }

    @Test
    void aPowerIsComputedInTheRingRatherThanReducedAfterwards() {
        // The whole reason a ring is worth having: the other way round this is a number with 845,000
        // digits.
        assertEquals(
                ModuloForm.of(9, 13),
                new ModuloForm(BigInteger.valueOf(7), BigInteger.valueOf(13)).power(BigInteger.valueOf(1000000)));
    }

    @Test
    void aNegativePower() {
        // 2^-1 mod 7 is the inverse of two, which is four.
        assertEquals(ModuloForm.of(4, 7), ModuloForm.of(2, 7).power(BigInteger.valueOf(-1)));
    }

    @Test
    void aModulusHasToBePositive() {
        assertThrows(ArithmeticException.class, () -> ModuloForm.of(1, 0));
        assertThrows(ArithmeticException.class, () -> ModuloForm.of(1, -7));
    }
}
