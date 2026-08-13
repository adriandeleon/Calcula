package com.calcula.machine;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How an inexact number is shown. Calc's {@code d n}, {@code d f}, {@code d s} and {@code d e}.
 *
 * <p><b>Display only, and that word is doing real work here.</b> The value keeps every digit it had;
 * this decides how many of them reach the screen. Rounding the value instead would be silent data loss
 * the moment a sheet was saved, because {@code .calc} writes a stack by formatting it — and
 * {@link com.calcula.parse.Formatter} therefore never consults this. A number is written to a file the
 * way a parser can read it back, and shown to a person the way a person can read it.
 *
 * <p>The default is {@link Style#NORMAL}, which is every digit the value carries. That is the right
 * default for a calculator whose whole argument is exactness, and the wrong one for reading an answer
 * at a glance: at the precision of 1000 that {@link Modes} permits, one number is a thousand
 * characters across the stack.
 *
 * @param style which of the four
 * @param digits how many after the point — ignored by {@link Style#NORMAL}, which has nothing to round
 */
public record FloatFormat(Style style, int digits) {

    public enum Style {
        /** Everything the value carries. */
        NORMAL("normal", ""),
        /** A fixed number of places after the point. */
        FIXED("fixed", "fix"),
        /** One digit before the point, and an exponent. */
        SCIENTIFIC("scientific", "sci"),
        /** As scientific, but the exponent is always a multiple of three. */
        ENGINEERING("engineering", "eng");

        private final String id;
        private final String label;

        Style(String id, String label) {
            this.id = id;
            this.label = label;
        }

        /** The word this is saved as, in a sheet and in the settings file. */
        public String id() {
            return id;
        }

        /** How it appears in the mode line; empty for the default, which needs no announcement. */
        public String label() {
            return label;
        }

        public static Style byId(String candidate) {
            for (Style style : values()) {
                if (style.id.equalsIgnoreCase(candidate)) {
                    return style;
                }
            }
            return null;
        }
    }

    public static final int MIN_DIGITS = 0;
    public static final int MAX_DIGITS = 100;
    public static final int DEFAULT_DIGITS = 6;

    /** Every digit, which is where a calculator that keeps them all should start. */
    public static final FloatFormat NORMAL = new FloatFormat(Style.NORMAL, DEFAULT_DIGITS);

    public FloatFormat {
        if (style == null) {
            throw new IllegalArgumentException("null float style");
        }
        if (digits < MIN_DIGITS || digits > MAX_DIGITS) {
            throw new IllegalArgumentException(
                    "digits must be between " + MIN_DIGITS + " and " + MAX_DIGITS + ", got " + digits);
        }
    }

    public FloatFormat withStyle(Style newStyle) {
        return new FloatFormat(newStyle, digits);
    }

    public FloatFormat withDigits(int newDigits) {
        return new FloatFormat(style, newDigits);
    }

    /** The mode-line fragment: empty for normal, else {@code "fix 4"}. */
    public String describe() {
        return style == Style.NORMAL ? "" : style.label() + " " + digits;
    }

    /** How this number should be read. */
    public String format(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return switch (style) {
            case NORMAL -> value.toPlainString();
            case FIXED -> value.setScale(digits, RoundingMode.HALF_UP).toPlainString();
            case SCIENTIFIC -> exponential(value, 1);
            case ENGINEERING -> exponential(value, 3);
        };
    }

    /**
     * Mantissa and exponent, with the exponent a multiple of {@code step}.
     *
     * <p>Rounding is applied and then <em>checked</em>, because rounding can carry: 9.99 to one place
     * is 10.0, which is no longer a mantissa. One correction is always enough — a carry can only ever
     * push it to exactly the next power — and it is cheaper to make than to reason about not needing.
     */
    private String exponential(BigDecimal value, int step) {
        if (value.signum() == 0) {
            // Nothing to normalise, and 0.0e0 reads as a mistake rather than as zero.
            return BigDecimal.ZERO.setScale(digits, RoundingMode.UNNECESSARY).toPlainString();
        }
        int exponent = alignedExponent(value.precision() - value.scale() - 1, step);
        BigDecimal mantissa = value.movePointLeft(exponent).setScale(digits, RoundingMode.HALF_UP);
        BigDecimal ceiling = BigDecimal.TEN.pow(step);
        if (mantissa.abs().compareTo(ceiling) >= 0) {
            exponent += step;
            mantissa = value.movePointLeft(exponent).setScale(digits, RoundingMode.HALF_UP);
        }
        return mantissa.toPlainString() + "e" + exponent;
    }

    /** The exponent rounded DOWN to a multiple of the step, so the mantissa stays at or above one. */
    private static int alignedExponent(int exponent, int step) {
        return step == 1 ? exponent : Math.floorDiv(exponent, step) * step;
    }
}
