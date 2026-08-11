package com.calcula.plot;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Axis tick positions on round numbers.
 *
 * <p>Dividing a range into equal parts gives ticks at 0.7143, 1.4286, …, which are unreadable. The
 * standard fix is to round the step up to the nearest 1, 2 or 5 times a power of ten, so ticks land
 * where a person would put them.
 */
public final class Ticks {

    private Ticks() {}

    /** A round step covering {@code span} in roughly {@code target} divisions. */
    public static double step(double span, int target) {
        if (!(span > 0) || target < 1) {
            throw new IllegalArgumentException("span " + span + ", target " + target);
        }
        double rough = span / target;
        double magnitude = Math.pow(10, Math.floor(Math.log10(rough)));
        double normalised = rough / magnitude;
        // 1, 2, 5, 10 — the steps people actually read.
        double nice = normalised <= 1 ? 1 : normalised <= 2 ? 2 : normalised <= 5 ? 5 : 10;
        return nice * magnitude;
    }

    /** Every round position within {@code [min, max]}. */
    public static double[] positions(double min, double max, int target) {
        double step = step(max - min, target);
        double first = Math.ceil(min / step) * step;
        int count = (int) Math.floor((max - first) / step) + 1;
        if (count <= 0) {
            return new double[0];
        }
        double[] out = new double[count];
        for (int i = 0; i < count; i++) {
            // Multiply rather than accumulate: adding the step repeatedly drifts, and the drift shows
            // up as a tick labelled 0.30000000000000004.
            out[i] = first + i * step;
        }
        return out;
    }

    /**
     * A label with only the digits the step justifies.
     *
     * <p>Deriving the precision from the step is what keeps an axis from reading 0.1, 0.2,
     * 0.30000000000000004 — the value is a binary double and cannot be exactly a tenth, so it has to be
     * rounded for display rather than printed raw.
     */
    public static String label(double value, double step) {
        int decimals = Math.max(0, (int) Math.ceil(-Math.log10(step)) + 1);
        if (decimals > 10) {
            return String.format("%.3e", value);
        }
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP);
        String text = rounded.stripTrailingZeros().toPlainString();
        return text.equals("-0") ? "0" : text;
    }
}
