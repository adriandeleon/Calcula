package com.calcula.plot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Turns a function into polylines to draw, breaking the line where the curve is not continuous.
 *
 * <p>The breaking is the whole point. Sampling {@code tan(x)} or {@code 1/x} uniformly and joining
 * every point produces a near-vertical stroke straight through each pole — the single most recognisable
 * bug in home-made plotters, and one that draws a line where the function has no value at all. Here a
 * jump too large to be real ends the segment, so the branches are drawn separately and the gap is
 * visible.
 */
public final class Sampler {

    /** More than one sample per pixel, so a wiggle finer than the pixel grid is not aliased away. */
    public static final int SAMPLES_PER_PIXEL = 2;

    /**
     * A vertical jump larger than this many screen-heights between adjacent samples is taken to be a
     * discontinuity rather than a steep slope.
     *
     * <p>It is a heuristic, and it has to be: telling a pole from a genuinely steep function needs the
     * limit, which is the engine's business rather than the sampler's. Two screen-heights is generous
     * enough that a steep-but-real curve stays joined.
     */
    public static final double BREAK_FACTOR = 2.0;

    private Sampler() {}

    /** One continuous run of the curve. Flat arrays, because they go straight to the canvas. */
    public record Segment(double[] xs, double[] ys, int length) {

        public boolean drawable() {
            return length >= 2;
        }
    }

    /** Sample across the viewport's x range, in world coordinates. */
    public static List<Segment> sample(DoubleUnaryOperator f, Viewport view) {
        int count = Math.max(2, (int) (view.width() * SAMPLES_PER_PIXEL));
        double step = view.xSpan() / (count - 1);
        double breakThreshold = view.ySpan() * BREAK_FACTOR;
        // Let the curve leave the view at a believable angle rather than clipping it to the edge,
        // but do not hand the canvas coordinates in the millions.
        double limit = view.ySpan() * 8;
        double centre = (view.yMin() + view.yMax()) / 2;

        List<Segment> segments = new ArrayList<>();
        double[] xs = new double[count];
        double[] ys = new double[count];
        int n = 0;
        double previous = Double.NaN;

        for (int i = 0; i < count; i++) {
            double x = view.xMin() + i * step;
            double y = valueAt(f, x);

            boolean usable = Double.isFinite(y);
            boolean jumped = usable && n > 0 && Math.abs(y - previous) > breakThreshold;

            if (!usable || jumped) {
                n = flush(segments, xs, ys, n);
                previous = Double.NaN;
                if (!usable) {
                    continue;
                }
            }
            xs[n] = x;
            ys[n] = clamp(y, centre, limit);
            n++;
            previous = y;
        }
        flush(segments, xs, ys, n);
        return segments;
    }

    private static double valueAt(DoubleUnaryOperator f, double x) {
        try {
            return f.applyAsDouble(x);
        } catch (RuntimeException e) {
            // A compiled closure should not throw, but a domain error from an exotic head would end
            // the whole plot rather than leaving one gap. A gap is the better answer.
            return Double.NaN;
        }
    }

    private static double clamp(double y, double centre, double limit) {
        return Math.max(centre - limit, Math.min(centre + limit, y));
    }

    private static int flush(List<Segment> into, double[] xs, double[] ys, int n) {
        if (n >= 2) {
            double[] cutX = new double[n];
            double[] cutY = new double[n];
            System.arraycopy(xs, 0, cutX, 0, n);
            System.arraycopy(ys, 0, cutY, 0, n);
            into.add(new Segment(cutX, cutY, n));
        }
        return 0;
    }

    /**
     * A y range that shows the interesting part of the curve, ignoring excursions towards a pole.
     *
     * <p>Auto-ranging on the raw minimum and maximum is useless for anything with an asymptote: one
     * sample near a pole is worth a million, and the rest of the curve is squashed into a line. Taking
     * a middle quantile of the sampled values instead keeps the shape visible.
     */
    public static double[] interestingRange(DoubleUnaryOperator f, double xMin, double xMax, int samples) {
        double[] values = new double[samples];
        int n = 0;
        for (int i = 0; i < samples; i++) {
            double y = valueAt(f, xMin + (xMax - xMin) * i / (double) (samples - 1));
            if (Double.isFinite(y)) {
                values[n++] = y;
            }
        }
        if (n == 0) {
            return new double[] {-1, 1};
        }
        double[] finite = new double[n];
        System.arraycopy(values, 0, finite, 0, n);
        java.util.Arrays.sort(finite);
        double low = finite[(int) (n * 0.05)];
        double high = finite[Math.min(n - 1, (int) (n * 0.95))];
        if (!(high > low)) {
            // A constant, or nearly so: give it room rather than a zero-height view.
            double centre = finite[n / 2];
            double pad = Math.max(1, Math.abs(centre) * 0.5);
            return new double[] {centre - pad, centre + pad};
        }
        double margin = (high - low) * 0.1;
        return new double[] {low - margin, high + margin};
    }
}
