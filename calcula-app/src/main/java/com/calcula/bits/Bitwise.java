package com.calcula.bits;

import java.math.BigInteger;

/**
 * The operations a word has, as opposed to the ones a number has.
 *
 * <p>Every one of these is meaningless without a <b>word size</b>, which is why Calc makes it a mode
 * rather than an argument. {@code not 12} is 3 in four bits, 243 in eight and 4294967283 in
 * thirty-two, and there is no answer that is right independently of the question "how wide". A
 * calculator that picked one silently would be wrong on most of them.
 *
 * <p>Values are read as two's complement of that width and answered <b>unsigned</b>, in
 * {@code [0, 2^w)}. That is the half people disagree about, so it is stated: {@code -1} in eight bits
 * is 255 going in, and 255 is what comes back out.
 *
 * <p>Symja implements none of this — {@code BitAnd} comes back unevaluated and lower-cased — so it is
 * arithmetic written here, like the money functions.
 */
public final class Bitwise {

    /** Calc's default, and the width most people mean when they do not say. */
    public static final int DEFAULT_WORD_SIZE = 32;

    public static final int MIN_WORD_SIZE = 1;

    /** Wide enough for any address anybody is bit-twiddling, and bounded so a mask cannot run away. */
    public static final int MAX_WORD_SIZE = 1024;

    private Bitwise() {}

    /** All ones, at this width. */
    public static BigInteger mask(int wordSize) {
        return BigInteger.ONE.shiftLeft(checked(wordSize)).subtract(BigInteger.ONE);
    }

    /** The value as it sits in a word of this width: two's complement in, unsigned out. */
    public static BigInteger truncate(BigInteger value, int wordSize) {
        return value.and(mask(wordSize));
    }

    public static BigInteger and(BigInteger a, BigInteger b, int wordSize) {
        return truncate(a, wordSize).and(truncate(b, wordSize));
    }

    public static BigInteger or(BigInteger a, BigInteger b, int wordSize) {
        return truncate(a, wordSize).or(truncate(b, wordSize));
    }

    public static BigInteger xor(BigInteger a, BigInteger b, int wordSize) {
        return truncate(a, wordSize).xor(truncate(b, wordSize));
    }

    /** Ones' complement within the word — the bits that were not set. */
    public static BigInteger not(BigInteger a, int wordSize) {
        return truncate(a, wordSize).xor(mask(wordSize));
    }

    /**
     * Shift left, dropping what falls off the top.
     *
     * <p>A negative count shifts the other way, because that is what everybody expects of a single
     * shift function and it saves an argument nobody wants to think about.
     */
    public static BigInteger shiftLeft(BigInteger a, int places, int wordSize) {
        if (places < 0) {
            return shiftRight(a, -places, wordSize);
        }
        return truncate(truncate(a, wordSize).shiftLeft(places), wordSize);
    }

    /** Shift right, filling with zeros — logical rather than arithmetic, since the word is unsigned. */
    public static BigInteger shiftRight(BigInteger a, int places, int wordSize) {
        if (places < 0) {
            return shiftLeft(a, -places, wordSize);
        }
        return truncate(a, wordSize).shiftRight(places);
    }

    /** Rotate left: what falls off the top comes back at the bottom. */
    public static BigInteger rotateLeft(BigInteger a, int places, int wordSize) {
        int width = checked(wordSize);
        int by = Math.floorMod(places, width);
        BigInteger value = truncate(a, width);
        return truncate(value.shiftLeft(by).or(value.shiftRight(width - by)), width);
    }

    public static BigInteger rotateRight(BigInteger a, int places, int wordSize) {
        return rotateLeft(a, -places, wordSize);
    }

    private static int checked(int wordSize) {
        if (wordSize < MIN_WORD_SIZE || wordSize > MAX_WORD_SIZE) {
            throw new IllegalArgumentException(
                    "word size must be between " + MIN_WORD_SIZE + " and " + MAX_WORD_SIZE + ", got " + wordSize);
        }
        return wordSize;
    }
}
