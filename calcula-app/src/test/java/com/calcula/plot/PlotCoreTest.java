package com.calcula.plot;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

import com.calcula.expr.Exprs;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotCoreTest {

    private static DoubleUnaryOperator f(String source) {
        return ExprCompiler.compile(Parser.parse(source), "x");
    }

    // ---- the compiler ------------------------------------------------------------------------

    @Test
    void arithmeticAndTheUsualFunctions() {
        assertEquals(7, f("2*x + 1").applyAsDouble(3), 1e-12);
        assertEquals(Math.sin(1.5), f("sin(x)").applyAsDouble(1.5), 1e-12);
        assertEquals(Math.exp(2), f("exp(x)").applyAsDouble(2), 1e-12);
        assertEquals(8, f("x^3").applyAsDouble(2), 1e-12);
        assertEquals(0.5, f("1/x").applyAsDouble(2), 1e-12);
        assertEquals(Math.PI, f("pi").applyAsDouble(0), 1e-12);
    }

    @Test
    void aSecondFreeVariableIsRefusedRatherThanGuessed() {
        // Drawing one of the infinitely many curves you get by inventing a value for y would be worse
        // than saying so.
        PlotException e = assertThrows(PlotException.class, () -> f("x + y"));
        assertTrue(e.getMessage().contains("y"), e.getMessage());
        assertFalse(ExprCompiler.isPlottable(Parser.parse("x + y"), "x"));
    }

    @Test
    void anExpressionTheCompilerCannotEvaluateIsRefused() {
        assertThrows(PlotException.class, () -> f("integrate(x, x)"));
        assertTrue(ExprCompiler.isPlottable(Parser.parse("sin(x)/x"), "x"));
    }

    @Test
    void compiledEvaluationIsFastEnoughToDrawWith() {
        // The whole reason this class exists: through the CAS this measured 0.38 ms per point, which
        // is ~450 ms for one 1200-pixel frame. The bar here is deliberately loose — it is guarding an
        // order of magnitude, not a benchmark.
        DoubleUnaryOperator g = f("sin(x)*exp(-x/5) + x^2/10");
        long start = System.nanoTime();
        double sink = 0;
        for (int i = 0; i < 200_000; i++) {
            sink += g.applyAsDouble(i * 1e-4);
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(Double.isFinite(sink));
        assertTrue(millis < 500, "200k evaluations took " + millis + " ms — too slow to redraw with");
    }

    // ---- the viewport ------------------------------------------------------------------------

    @Test
    void worldAndScreenRoundTrip() {
        Viewport v = new Viewport(-10, 10, -5, 5, 200, 100);
        assertEquals(100, v.toScreenX(0), 1e-9);
        assertEquals(50, v.toScreenY(0), 1e-9);
        assertEquals(3.5, v.toWorldX(v.toScreenX(3.5)), 1e-9);
        assertEquals(-2.25, v.toWorldY(v.toScreenY(-2.25)), 1e-9);
    }

    @Test
    void screenYIsFlippedBecauseWorldYPointsUp() {
        // The classic plotting bug, and it looks plausible for anything symmetric.
        Viewport v = new Viewport(0, 1, 0, 10, 100, 100);
        assertTrue(v.toScreenY(9) < v.toScreenY(1), "a larger y should be higher on screen");
        assertEquals(0, v.toScreenY(10), 1e-9);
        assertEquals(100, v.toScreenY(0), 1e-9);
    }

    @Test
    void zoomKeepsTheAnchorUnderTheCursor() {
        Viewport v = new Viewport(-10, 10, -10, 10, 200, 200);
        double screenX = 150;
        double screenY = 40;
        double beforeX = v.toWorldX(screenX);
        double beforeY = v.toWorldY(screenY);

        Viewport zoomed = v.zoomAt(2.0, screenX, screenY);

        assertEquals(beforeX, zoomed.toWorldX(screenX), 1e-9, "the point under the cursor should not move");
        assertEquals(beforeY, zoomed.toWorldY(screenY), 1e-9);
        assertTrue(zoomed.xSpan() < v.xSpan(), "zooming in should show less");
    }

    @Test
    void panMovesTheWorldWithTheDrag() {
        Viewport v = new Viewport(0, 10, 0, 10, 100, 100);
        Viewport dragged = v.pan(10, 0); // dragged right by a tenth of the width
        assertEquals(-1, dragged.xMin(), 1e-9, "dragging right should reveal smaller x");
    }

    @Test
    void anEmptyViewportIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Viewport(1, 1, 0, 1, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new Viewport(0, 1, 0, 1, 0, 10));
    }

    // ---- ticks -------------------------------------------------------------------------------

    @Test
    void ticksLandOnRoundNumbers() {
        assertEquals(2, Ticks.step(20, 10), 1e-12);
        assertEquals(0.5, Ticks.step(5, 10), 1e-12);
        assertEquals(20, Ticks.step(100, 5), 1e-12);

        double[] positions = Ticks.positions(-1, 1, 4);
        assertEquals(-1.0, positions[0], 1e-12);
        assertTrue(positions.length >= 3);
    }

    @Test
    void labelsShowOnlyTheDigitsTheStepJustifies() {
        // A tick is a binary double and cannot be exactly a tenth; printed raw it reads
        // 0.30000000000000004.
        assertEquals("0.3", Ticks.label(0.1 + 0.2, 0.1));
        assertEquals("10", Ticks.label(10, 1));
        assertEquals("0", Ticks.label(-0.0, 1), "negative zero should not print as -0");
    }

    // ---- the sampler -------------------------------------------------------------------------

    @Test
    void aContinuousCurveIsOneUnbrokenRun() {
        Viewport v = new Viewport(-3, 3, -2, 2, 300, 200);
        List<Sampler.Segment> segments = Sampler.sample(f("sin(x)"), v);
        assertEquals(1, segments.size(), "sin has nothing to break at");
    }

    @Test
    void aPoleBreaksTheLineRatherThanDrawingThroughIt() {
        // The most recognisable bug in home-made plotters: joining across the asymptote draws a
        // near-vertical stroke where the function has no value at all.
        Viewport v = new Viewport(-3, 3, -10, 10, 300, 200);
        List<Sampler.Segment> segments = Sampler.sample(f("1/x"), v);
        assertEquals(2, segments.size(), "1/x should be drawn as two branches");

        List<Sampler.Segment> tangent = Sampler.sample(f("tan(x)"), new Viewport(-5, 5, -10, 10, 300, 200));
        assertTrue(tangent.size() >= 3, "tan should break at each pole, got " + tangent.size());
    }

    @Test
    void undefinedRegionsAreSimplyAbsent() {
        // sqrt is undefined below zero; the curve should start at the origin, not wander.
        Viewport v = new Viewport(-4, 4, -1, 3, 200, 200);
        List<Sampler.Segment> segments = Sampler.sample(f("sqrt(x)"), v);
        assertEquals(1, segments.size());
        assertTrue(segments.get(0).xs()[0] >= -0.1, "should begin at zero, not at -4");
    }

    @Test
    void theAutomaticRangeIgnoresExcursionsTowardsAPole() {
        // Auto-ranging on the raw extremes is useless with an asymptote: one sample near the pole is
        // worth a million and squashes the rest of the curve into a line.
        double[] range = Sampler.interestingRange(f("1/x"), -5, 5, 500);
        assertTrue(range[1] - range[0] < 100, "range " + range[0] + ".." + range[1] + " is dominated by the pole");
    }

    @Test
    void aConstantStillGetsAViewWithHeight() {
        double[] range = Sampler.interestingRange(f("3"), -1, 1, 100);
        assertTrue(range[1] > range[0], "a flat function must not produce a zero-height view");
    }

    // ---- the stack value ---------------------------------------------------------------------

    @Test
    void aPlotIsAnOrdinaryValueThatCanSitOnTheStack() {
        var plot = PlotValue.of(Parser.parse("sin(x)"), "x", -10, 10);
        assertTrue(PlotValue.isPlot(plot));
        assertEquals(Parser.parse("sin(x)"), PlotValue.body(plot));
        assertEquals("x", PlotValue.variable(plot));
        assertEquals(-10, PlotValue.xMin(plot), 1e-12);
        assertEquals(10, PlotValue.xMax(plot), 1e-12);

        assertFalse(PlotValue.isPlot(Exprs.of(1)));
        assertThrows(PlotException.class, () -> PlotValue.of(Parser.parse("x"), "x", 1, 1));
    }
}
