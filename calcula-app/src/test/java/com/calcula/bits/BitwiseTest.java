package com.calcula.bits;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BitwiseTest {

    private static BigInteger n(long value) {
        return BigInteger.valueOf(value);
    }

    @Test
    void theEasyThree() {
        assertEquals(n(8), Bitwise.and(n(12), n(10), 8));
        assertEquals(n(14), Bitwise.or(n(12), n(10), 8));
        assertEquals(n(6), Bitwise.xor(n(12), n(10), 8));
    }

    @Test
    void notDependsEntirelyOnTheWordSize() {
        // The reason the width is a mode and not a default. Three answers, all correct.
        assertEquals(n(3), Bitwise.not(n(12), 4));
        assertEquals(n(243), Bitwise.not(n(12), 8));
        assertEquals(n(4294967283L), Bitwise.not(n(12), 32));
    }

    @Test
    void aNegativeGoesInAsTwosComplementAndComesOutUnsigned() {
        assertEquals(n(255), Bitwise.truncate(n(-1), 8));
        assertEquals(n(240), Bitwise.and(n(-1), n(240), 8));
    }

    @Test
    void shiftingDropsWhatFallsOffTheTop() {
        assertEquals(n(16), Bitwise.shiftLeft(n(1), 4, 8));
        assertEquals(n(0), Bitwise.shiftLeft(n(1), 8, 8), "off the end of the word");
        assertEquals(n(4), Bitwise.shiftRight(n(16), 2, 8));
    }

    @Test
    void aNegativeCountShiftsTheOtherWay() {
        assertEquals(Bitwise.shiftRight(n(16), 2, 8), Bitwise.shiftLeft(n(16), -2, 8));
        assertEquals(Bitwise.shiftLeft(n(1), 4, 8), Bitwise.shiftRight(n(1), -4, 8));
    }

    @Test
    void rotatingKeepsEveryBit() {
        assertEquals(n(1), Bitwise.rotateLeft(n(128), 1, 8), "the top bit comes back at the bottom");
        assertEquals(n(128), Bitwise.rotateRight(n(1), 1, 8));
        assertEquals(n(0b10110011), Bitwise.rotateLeft(n(0b10110011), 8, 8), "a full turn changes nothing");
    }

    @Test
    void shiftingRightFillsWithZerosRatherThanTheSignBit() {
        // Logical, not arithmetic: the word is unsigned once it is in, so there is no sign to extend.
        assertEquals(n(127), Bitwise.shiftRight(n(-1), 1, 8));
    }

    @Test
    void anAbsurdWordSizeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Bitwise.and(n(1), n(1), 0));
        assertThrows(IllegalArgumentException.class, () -> Bitwise.and(n(1), n(1), Bitwise.MAX_WORD_SIZE + 1));
    }
}
