package com.calcula.plot;

import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

import com.calcula.expr.Expr;
import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The machinery a surface needs, before anything is drawn.
 *
 * <p>All of it is arithmetic and none of it is pixels, which is the point of doing this part first:
 * whether a surface is correct is decided here, and whether it is pretty is decided later.
 */
class SurfaceCoreTest {

    private static final double EPS = 1e-9;

    // ---- the compiler, widened ---------------------------------------------------------------

    @Test
    void aFunctionOfTwoVariablesCompiles() {
        DoubleBinaryOperator f = ExprCompiler.compile(Parser.parse("x^2 + y^2"), "x", "y");
        assertEquals(0, f.applyAsDouble(0, 0), EPS);
        assertEquals(25, f.applyAsDouble(3, 4), EPS);
    }

    /** Which name is which, and that swapping them is not the same function. */
    @Test
    void theVariablesAreNotInterchangeable() {
        DoubleBinaryOperator f = ExprCompiler.compile(Parser.parse("x - y"), "x", "y");
        assertEquals(1, f.applyAsDouble(3, 2), EPS);
        assertEquals(-1, f.applyAsDouble(2, 3), EPS);
    }

    @Test
    void namingThemTheOtherWayRoundSwapsTheAxes() {
        Expr e = Parser.parse("x - y");
        assertEquals(1, ExprCompiler.compile(e, "x", "y").applyAsDouble(3, 2), EPS);
        assertEquals(-1, ExprCompiler.compile(e, "y", "x").applyAsDouble(3, 2), EPS);
    }

    /**
     * The refusal that moved rather than disappeared. A second free name is exactly what a surface
     * wants; a third is still nothing anyone can draw.
     */
    @Test
    void aThirdFreeVariableIsStillRefused() {
        PlotException e =
                assertThrows(PlotException.class, () -> ExprCompiler.compile(Parser.parse("x + y + w"), "x", "y"));
        assertTrue(e.getMessage().contains("w"), e.getMessage());
    }

    @Test
    void aSecondFreeVariableIsStillRefusedForACurve() {
        assertThrows(PlotException.class, () -> ExprCompiler.compile(Parser.parse("x + y"), "x"));
    }

    @Test
    void aSurfaceNeedsTwoDifferentNames() {
        assertThrows(PlotException.class, () -> ExprCompiler.compile(Parser.parse("x + x"), "x", "x"));
    }

    /**
     * The whole reason there is one compiler and not two: a curve and a surface must agree about what
     * a formula means. Holding y fixed at a value must give the same numbers as compiling the
     * substituted expression as a curve.
     */
    @Test
    void aSurfaceAndItsCrossSectionAgree() {
        DoubleBinaryOperator surface = ExprCompiler.compile(Parser.parse("sin(x)*cos(y) + x^2/4"), "x", "y");
        DoubleUnaryOperator crossSection = ExprCompiler.compile(Parser.parse("sin(x)*cos(2) + x^2/4"), "x");
        for (double x = -3; x <= 3; x += 0.25) {
            assertEquals(crossSection.applyAsDouble(x), surface.applyAsDouble(x, 2), 1e-12, "at x=" + x);
        }
    }

    /** Curves must be exactly as they were; the widening is not allowed to have cost them anything. */
    @Test
    void everyCurveStillCompilesAsBefore() {
        assertEquals(4, ExprCompiler.compile(Parser.parse("x^2"), "x").applyAsDouble(2), EPS);
        assertEquals(1, ExprCompiler.compile(Parser.parse("sin(x)"), "x").applyAsDouble(Math.PI / 2), EPS);
        assertEquals(2, ExprCompiler.compile(Parser.parse("max(1, x)"), "x").applyAsDouble(2), EPS);
        assertTrue(ExprCompiler.isPlottable(Parser.parse("1/(x - 1)"), "x"));
        assertFalse(ExprCompiler.isPlottable(Parser.parse("Solve(x)"), "x"));
    }

    @Test
    void isPlottableAnswersForSurfacesToo() {
        assertTrue(ExprCompiler.isPlottable(Parser.parse("x*y"), "x", "y"));
        assertFalse(ExprCompiler.isPlottable(Parser.parse("x*y*w"), "x", "y"));
    }

    // ---- the value ---------------------------------------------------------------------------

    /**
     * The property the file format rests on. A sheet saves a stack value by formatting it and loads it
     * by parsing that back, so a value that cannot make the trip is data loss at SAVE time.
     */
    @Test
    void aSurfaceValueSurvivesBeingWrittenDown() {
        Expr surface = SurfaceValue.of(Parser.parse("x^2 + y^2"), "x", "y", -3, 3, -3, 3);
        Expr back = Parser.parse(Formatter.format(surface));

        assertEquals(surface, back, SurfaceValue.ROUND_TRIPS);
        assertTrue(SurfaceValue.isSurface(back));
        assertEquals("x", SurfaceValue.xVariable(back));
        assertEquals("y", SurfaceValue.yVariable(back));
        assertEquals(-3, SurfaceValue.xMin(back), EPS);
        assertEquals(3, SurfaceValue.yMax(back), EPS);
    }

    /** A curve and a surface must not answer yes to each other's question. */
    @Test
    void aSurfaceIsNotAPlotAndAPlotIsNotASurface() {
        Expr surface = SurfaceValue.of(Parser.parse("x*y"), "x", "y", -1, 1, -1, 1);
        Expr curve = PlotValue.of(Parser.parse("x^2"), "x", -1, 1);

        assertTrue(SurfaceValue.isSurface(surface));
        assertFalse(PlotValue.isPlot(surface));
        assertTrue(PlotValue.isPlot(curve));
        assertFalse(SurfaceValue.isSurface(curve));
    }

    @Test
    void anEmptyRangeIsRefused() {
        Expr body = Parser.parse("x*y");
        assertThrows(PlotException.class, () -> SurfaceValue.of(body, "x", "y", 1, 1, -1, 1));
        assertThrows(PlotException.class, () -> SurfaceValue.of(body, "x", "y", -1, 1, 2, 1));
    }

    /** Reading order, not alphabetical: quietly swapping someone's axes is a confusing tidiness. */
    @Test
    void theVariablesAreFoundInTheOrderTheyAreWritten() {
        assertEquals(List.of("y", "x"), SurfaceValue.inferVariables(Parser.parse("y*sin(x)")));
        assertEquals(List.of("a", "b"), SurfaceValue.inferVariables(Parser.parse("a + b")));
    }

    @Test
    void aKnownConstantIsNotAVariable() {
        assertEquals(List.of("x", "y"), SurfaceValue.inferVariables(Parser.parse("Pi*x + E*y")));
    }

    @Test
    void theWrongNumberOfVariablesSaysHowMany() {
        assertThrows(PlotException.class, () -> SurfaceValue.inferVariables(Parser.parse("x^2")));
        assertThrows(PlotException.class, () -> SurfaceValue.inferVariables(Parser.parse("x + y + w")));
    }

    // ---- the grid ----------------------------------------------------------------------------

    @Test
    void theGridCoversTheWholeRange() {
        SurfaceSampler.Grid g = SurfaceSampler.sample((x, y) -> x + y, -2, 2, -1, 5, 5);

        assertEquals(5, g.steps());
        assertEquals(-2, g.xs()[0], EPS);
        assertEquals(2, g.xs()[4], EPS, "the last sample is the end of the range, not near it");
        assertEquals(-1, g.ys()[0], EPS);
        assertEquals(5, g.ys()[4], EPS);
        assertEquals(-3, g.z()[0][0], EPS);
        assertEquals(7, g.z()[4][4], EPS);
    }

    /** z[i][j] is the height at xs[i], ys[j] — asymmetric on purpose, so a transpose would fail. */
    @Test
    void theGridIsIndexedByXThenY() {
        SurfaceSampler.Grid g = SurfaceSampler.sample((x, y) -> 10 * x + y, 0, 1, 0, 1, 2);
        assertEquals(10, g.z()[1][0], EPS, "x=1, y=0");
        assertEquals(1, g.z()[0][1], EPS, "x=0, y=1");
    }

    /**
     * A hole rather than a stroke. A curve has to break its line at a pole; a surface just does not
     * draw the cell, and the gap is the truth.
     */
    @Test
    void aPointWithNoValueBecomesAHole() {
        SurfaceSampler.Grid g = SurfaceSampler.sample((x, y) -> 1 / (x * x + y * y), -1, 1, -1, 1, 3);

        assertFalse(g.finiteAt(1, 1), "the pole at the origin");
        assertTrue(g.finiteAt(0, 0));
        assertFalse(g.quadFinite(0, 0), "a cell touching the pole cannot be drawn");
        assertFalse(g.isEmpty(), "but the rest of the surface is still there");
    }

    @Test
    void aThrownEvaluationIsAHoleAndNotAFailure() {
        SurfaceSampler.Grid g = SurfaceSampler.sample(
                (x, y) -> {
                    if (x > 0) {
                        throw new IllegalStateException("nope");
                    }
                    return x + y;
                },
                -1,
                1,
                -1,
                1,
                3);
        assertTrue(g.finiteAt(0, 0));
        assertFalse(g.finiteAt(2, 0), "one bad point must not end the sample");
    }

    @Test
    void theHeightRangeIgnoresTheHoles() {
        SurfaceSampler.Grid g = SurfaceSampler.sample((x, y) -> x == 0 ? Double.NaN : x, -1, 1, -1, 1, 3);
        double[] range = g.zRange();
        assertEquals(-1, range[0], EPS);
        assertEquals(1, range[1], EPS);
    }

    @Test
    void aSurfaceWithNoFinitePointAnywhereSaysSo() {
        SurfaceSampler.Grid g = SurfaceSampler.sample((x, y) -> Double.NaN, -1, 1, -1, 1, 4);
        assertTrue(g.isEmpty());
        assertTrue(Double.isNaN(g.zRange()[0]));
    }

    @Test
    void theGridSizeIsClampedToSomethingDrawable() {
        assertEquals(2, SurfaceSampler.sample((x, y) -> 0, 0, 1, 0, 1, 1).steps());
        assertEquals(
                SurfaceSampler.MAX_STEPS,
                SurfaceSampler.sample((x, y) -> 0, 0, 1, 0, 1, 10_000).steps());
    }

    @Test
    void anEmptyRangeIsRefusedBeforeAnythingIsAllocated() {
        assertThrows(PlotException.class, () -> SurfaceSampler.sample((x, y) -> 0, 1, 1, 0, 1, 10));
    }

    // ---- end to end, still without a pixel ---------------------------------------------------

    @Test
    void aValueOnTheStackCanBeSampled() {
        Expr surface = SurfaceValue.of(Parser.parse("x^2 + y^2"), "x", "y", -2, 2, -2, 2);

        DoubleBinaryOperator f = ExprCompiler.compile(
                SurfaceValue.body(surface), SurfaceValue.xVariable(surface), SurfaceValue.yVariable(surface));
        SurfaceSampler.Grid g = SurfaceSampler.sample(
                f,
                SurfaceValue.xMin(surface),
                SurfaceValue.xMax(surface),
                SurfaceValue.yMin(surface),
                SurfaceValue.yMax(surface),
                5);

        assertEquals(0, g.z()[2][2], EPS, "the bowl's lowest point is at the origin");
        assertEquals(8, g.z()[0][0], EPS, "and its corner is x^2 + y^2 at (-2, -2)");
        assertEquals(0, g.zRange()[0], EPS);
        assertEquals(8, g.zRange()[1], EPS);
    }
}
