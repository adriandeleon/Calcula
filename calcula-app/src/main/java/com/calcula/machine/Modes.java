package com.calcula.machine;

import java.math.MathContext;

import com.calcula.bits.Bitwise;

/**
 * The mode line, as data. Every field here is something Calc shows across the bottom of the window and
 * lets you flip with a two-key command.
 *
 * @param angle how trigonometric arguments are read
 * @param precision working digits for inexact arithmetic
 * @param symbolic keep exact results symbolic rather than evaluating them numerically
 * @param fractions prefer an exact fraction over a decimal where there is a choice
 * @param floats how an inexact number is shown -- display only, never what is stored
 * @param wordSize how wide a word is, for the bitwise operations that mean nothing without one
 * @param radix the base whole numbers are shown in -- display only, like {@code floats}
 * @param simplification how hard to work on an answer
 */
public record Modes(
        Angle angle,
        int precision,
        boolean symbolic,
        boolean fractions,
        FloatFormat floats,
        int wordSize,
        int radix,
        Simplification simplification) {

    /** Base ten, and the only base in which the grouping and the decimal point mean what they say. */
    public static final int DECIMAL = 10;

    public static final int MIN_RADIX = 2;

    /** Character.MAX_RADIX: past thirty-six there are no more digits to write with. */
    public static final int MAX_RADIX = 36;

    /** Calc's own defaults: radians, twelve digits, symbolic, fractions preferred, every digit shown. */
    public static final Modes DEFAULTS = new Modes(
            Angle.RADIANS,
            12,
            true,
            true,
            FloatFormat.NORMAL,
            Bitwise.DEFAULT_WORD_SIZE,
            DECIMAL,
            Simplification.DEFAULT);

    /** The four that were here before display and words, for the callers that do not care about either. */
    public Modes(Angle angle, int precision, boolean symbolic, boolean fractions) {
        this(
                angle,
                precision,
                symbolic,
                fractions,
                FloatFormat.NORMAL,
                Bitwise.DEFAULT_WORD_SIZE,
                DECIMAL,
                Simplification.DEFAULT);
    }

    public static final int MIN_PRECISION = 3;
    public static final int MAX_PRECISION = 1000;

    public Modes {
        if (angle == null) {
            throw new IllegalArgumentException("null angle mode");
        }
        if (floats == null) {
            throw new IllegalArgumentException("null float format");
        }
        if (wordSize < Bitwise.MIN_WORD_SIZE || wordSize > Bitwise.MAX_WORD_SIZE) {
            throw new IllegalArgumentException("word size out of range: " + wordSize);
        }
        if (simplification == null) {
            throw new IllegalArgumentException("null simplification level");
        }
        if (radix < MIN_RADIX || radix > MAX_RADIX) {
            throw new IllegalArgumentException("radix must be between " + MIN_RADIX + " and " + MAX_RADIX);
        }
        if (precision < MIN_PRECISION || precision > MAX_PRECISION) {
            throw new IllegalArgumentException(
                    "precision must be between " + MIN_PRECISION + " and " + MAX_PRECISION + ", got " + precision);
        }
    }

    public enum Angle {
        RADIANS("rad"),
        DEGREES("deg"),
        /** Gradians. Present because Calc has them; nobody will use them. */
        GRADIANS("grad");

        private final String label;

        Angle(String label) {
            this.label = label;
        }

        /** How it appears in the mode line. */
        public String label() {
            return label;
        }
    }

    public Modes withAngle(Angle newAngle) {
        return new Modes(newAngle, precision, symbolic, fractions, floats, wordSize, radix, simplification);
    }

    public Modes withPrecision(int newPrecision) {
        return new Modes(angle, newPrecision, symbolic, fractions, floats, wordSize, radix, simplification);
    }

    public Modes withSymbolic(boolean newSymbolic) {
        return new Modes(angle, precision, newSymbolic, fractions, floats, wordSize, radix, simplification);
    }

    public Modes withFractions(boolean newFractions) {
        return new Modes(angle, precision, symbolic, newFractions, floats, wordSize, radix, simplification);
    }

    public Modes withFloats(FloatFormat newFloats) {
        return new Modes(angle, precision, symbolic, fractions, newFloats, wordSize, radix, simplification);
    }

    public Modes withWordSize(int newWordSize) {
        return new Modes(angle, precision, symbolic, fractions, floats, newWordSize, radix, simplification);
    }

    public Modes withRadix(int newRadix) {
        return new Modes(angle, precision, symbolic, fractions, floats, wordSize, newRadix, simplification);
    }

    public Modes withSimplification(Simplification level) {
        return new Modes(angle, precision, symbolic, fractions, floats, wordSize, radix, level);
    }

    /** The rounding context inexact arithmetic works to. */
    public MathContext mathContext() {
        return new MathContext(precision);
    }

    /** The mode line text, e.g. {@code "rad  prec 12  fix 4  symb  frac"}. */
    public String describe() {
        StringBuilder out = new StringBuilder(angle.label()).append("  prec ").append(precision);
        if (!floats.describe().isEmpty()) {
            out.append("  ").append(floats.describe());
        }
        // Only when they are not the ones everybody assumes. A mode line that always states the base
        // is a mode line nobody reads.
        if (radix != DECIMAL) {
            out.append("  base ").append(radix);
        }
        if (wordSize != Bitwise.DEFAULT_WORD_SIZE) {
            out.append("  word ").append(wordSize);
        }
        if (simplification == Simplification.NONE) {
            // Named rather than abbreviated, because it is the one level that changes what an answer
            // IS rather than how hard it was worked on, and somebody who left it on by accident has
            // to be able to see why nothing is computing.
            out.append("  no eval");
        } else if (!simplification.label().isEmpty()) {
            out.append("  ").append(simplification.label());
        }
        if (symbolic) {
            out.append("  symb");
        }
        if (fractions) {
            out.append("  frac");
        }
        return out.toString();
    }
}
