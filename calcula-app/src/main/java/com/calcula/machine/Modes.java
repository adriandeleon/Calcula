package com.calcula.machine;

import java.math.MathContext;

/**
 * The mode line, as data. Every field here is something Calc shows across the bottom of the window and
 * lets you flip with a two-key command.
 *
 * @param angle how trigonometric arguments are read
 * @param precision working digits for inexact arithmetic
 * @param symbolic keep exact results symbolic rather than evaluating them numerically
 * @param fractions prefer an exact fraction over a decimal where there is a choice
 * @param floats how an inexact number is shown -- display only, never what is stored
 */
public record Modes(Angle angle, int precision, boolean symbolic, boolean fractions, FloatFormat floats) {

    /** Calc's own defaults: radians, twelve digits, symbolic, fractions preferred, every digit shown. */
    public static final Modes DEFAULTS = new Modes(Angle.RADIANS, 12, true, true, FloatFormat.NORMAL);

    /** The four that were here before floats, for the callers that do not care about display. */
    public Modes(Angle angle, int precision, boolean symbolic, boolean fractions) {
        this(angle, precision, symbolic, fractions, FloatFormat.NORMAL);
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
        return new Modes(newAngle, precision, symbolic, fractions, floats);
    }

    public Modes withPrecision(int newPrecision) {
        return new Modes(angle, newPrecision, symbolic, fractions, floats);
    }

    public Modes withSymbolic(boolean newSymbolic) {
        return new Modes(angle, precision, newSymbolic, fractions, floats);
    }

    public Modes withFractions(boolean newFractions) {
        return new Modes(angle, precision, symbolic, newFractions, floats);
    }

    public Modes withFloats(FloatFormat newFloats) {
        return new Modes(angle, precision, symbolic, fractions, newFloats);
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
        if (symbolic) {
            out.append("  symb");
        }
        if (fractions) {
            out.append("  frac");
        }
        return out.toString();
    }
}
