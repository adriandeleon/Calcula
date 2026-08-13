package com.calcula.plot;

import java.util.function.DoubleBinaryOperator;

/**
 * Turns a function of two variables into a grid of heights to draw.
 *
 * <p>The counterpart of {@link Sampler}, and the easier of the two. A curve has to decide where to
 * <em>break</em> the line, because joining across a pole draws a vertical stroke that is not part of
 * the function. A surface has no such stroke to draw: a quad with a corner that is not a finite
 * number is simply not drawn, and the hole in the surface is the truth.
 *
 * <p>Sampled once per function and range, not once per frame. Rotating re-projects the grid it
 * already has. That split is the whole performance story — and it is why plotting compiles to a
 * closure rather than going through the CAS, which measured 0.38 ms per point: one 60×60 surface
 * would be about 1.4 seconds of engine time, per frame.
 */
public final class SurfaceSampler {

    /**
     * Points along each edge.
     *
     * <p>Sixty is 3,600 evaluations of a compiled closure — microseconds — and about as fine as a
     * wireframe can be drawn before the lines merge into a solid block.
     */
    public static final int DEFAULT_STEPS = 60;

    /** Beyond this the grid costs more than the picture gains. */
    public static final int MAX_STEPS = 400;

    private SurfaceSampler() {}

    /**
     * A sampled surface.
     *
     * <p>{@code z[i][j]} is the height at {@code xs[i], ys[j]}, and is {@code NaN} where the function
     * has no finite value there. Callers must check: {@link #finiteAt} says whether a single point can
     * be drawn, {@link #quadFinite} whether a whole cell can.
     */
    public record Grid(double[] xs, double[] ys, double[][] z) {

        public int steps() {
            return xs.length;
        }

        public boolean finiteAt(int i, int j) {
            return Double.isFinite(z[i][j]);
        }

        /** Whether the cell whose lower corner is {@code (i, j)} has four finite corners. */
        public boolean quadFinite(int i, int j) {
            return finiteAt(i, j) && finiteAt(i + 1, j) && finiteAt(i, j + 1) && finiteAt(i + 1, j + 1);
        }

        /**
         * The height range actually present, ignoring the points that have none.
         *
         * <p>{@code {NaN, NaN}} when nothing is finite — a function with no drawable point anywhere,
         * which the caller should report rather than scale an empty axis to.
         */
        public double[] zRange() {
            double lo = Double.POSITIVE_INFINITY;
            double hi = Double.NEGATIVE_INFINITY;
            for (double[] column : z) {
                for (double value : column) {
                    if (Double.isFinite(value)) {
                        lo = Math.min(lo, value);
                        hi = Math.max(hi, value);
                    }
                }
            }
            return lo > hi ? new double[] {Double.NaN, Double.NaN} : new double[] {lo, hi};
        }

        public boolean isEmpty() {
            return Double.isNaN(zRange()[0]);
        }
    }

    public static Grid sample(DoubleBinaryOperator f, double xMin, double xMax, double yMin, double yMax) {
        return sample(f, xMin, xMax, yMin, yMax, DEFAULT_STEPS);
    }

    /**
     * @param steps points along each edge; clamped to something drawable
     */
    public static Grid sample(DoubleBinaryOperator f, double xMin, double xMax, double yMin, double yMax, int steps) {
        if (!(xMax > xMin) || !(yMax > yMin)) {
            throw new PlotException("empty range: " + xMin + " to " + xMax + ", " + yMin + " to " + yMax);
        }
        int n = Math.clamp(steps, 2, MAX_STEPS);
        double[] xs = axis(xMin, xMax, n);
        double[] ys = axis(yMin, yMax, n);
        double[][] z = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                z[i][j] = finite(f, xs[i], ys[j]);
            }
        }
        return new Grid(xs, ys, z);
    }

    /**
     * Evaluate, turning every way of having no value into {@code NaN}.
     *
     * <p>An infinity is as undrawable as a not-a-number, and a compiled closure can also throw —
     * {@code Math.pow} of a negative base to a fractional power, an array read inside a future node
     * type. One hole in a surface is a far better outcome than one bad point ending the sample.
     */
    private static double finite(DoubleBinaryOperator f, double x, double y) {
        try {
            double value = f.applyAsDouble(x, y);
            return Double.isFinite(value) ? value : Double.NaN;
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    /** Endpoints included, so the surface reaches the edges of the range it was asked for. */
    private static double[] axis(double min, double max, int n) {
        double[] values = new double[n];
        double step = (max - min) / (n - 1);
        for (int i = 0; i < n; i++) {
            values[i] = min + i * step;
        }
        // Set explicitly rather than left to accumulate: n-1 additions of a rounded step lands near
        // the end without reaching it, and a surface that stops just short of its own range looks
        // like a bug in the function.
        values[n - 1] = max;
        return values;
    }
}
