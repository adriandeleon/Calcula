package com.calcula.plot;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

import com.calcula.expr.Expr;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphicsAndAnalysisTest {

    private static Expr e(String source) {
        return Parser.parse(source);
    }

    // ---- reading the engine's own picture -----------------------------------------------------

    @Test
    void aLineWithAColourDirectiveIsRead() {
        // The exact shape Symja's Plot returns, written in our own notation.
        Expr graphics = e("Graphics([RGBColor(0.1, 0.2, 0.3), Line([[0,0],[1,1],[2,0]])],"
                + " Rule(PlotRange, [[0, 2], [0, 1]]))");
        GraphicsScene scene = GraphicsScene.parse(graphics);

        assertEquals(1, scene.primitives().size());
        GraphicsScene.Primitive.Polyline line =
                (GraphicsScene.Primitive.Polyline) scene.primitives().get(0);
        assertEquals(3, line.xs().length);
        assertEquals(2.0, line.xs()[2], 1e-12);
        assertEquals(0.2, line.color().green(), 1e-9, "the directive should apply to what follows it");
        assertEquals(0.0, scene.xRange()[0], 1e-12);
        assertEquals(2.0, scene.xRange()[1], 1e-12);
    }

    @Test
    void aDirectiveAppliesToEverythingAfterItAndNothingBefore() {
        Expr graphics = e("Graphics([Line([[0,0],[1,1]]), RGBColor(1, 0, 0), Line([[1,1],[2,2]])])");
        GraphicsScene scene = GraphicsScene.parse(graphics);

        assertEquals(2, scene.primitives().size());
        assertEquals(GraphicsScene.Rgb.DEFAULT, scene.primitives().get(0).color(), "before the directive");
        assertEquals(1.0, scene.primitives().get(1).color().red(), 1e-9, "after it");
    }

    @Test
    void pointsAndCirclesAreRead() {
        GraphicsScene points = GraphicsScene.parse(e("Graphics([Point([[1,1],[2,4]])])"));
        assertTrue(points.primitives().get(0) instanceof GraphicsScene.Primitive.Points);

        GraphicsScene circle = GraphicsScene.parse(e("Graphics([Circle([0,0], 1)])"));
        assertTrue(circle.primitives().get(0) instanceof GraphicsScene.Primitive.Circle);
    }

    @Test
    void anUnknownPrimitiveIsSkippedRatherThanRefusingThePicture() {
        // Drawing what we understand beats refusing everything over one directive we do not.
        GraphicsScene scene = GraphicsScene.parse(e("Graphics([Thickness(0.01), Line([[0,0],[1,1]]), Wibble(3)])"));
        assertEquals(1, scene.primitives().size());
    }

    @Test
    void aRangeIsDerivedFromTheDataWhenTheEngineGivesNone() {
        GraphicsScene scene = GraphicsScene.parse(e("Graphics([Line([[0,0],[4,8]])])"));
        assertTrue(scene.xRange()[0] <= 0 && scene.xRange()[1] >= 4);
        assertTrue(scene.yRange()[0] <= 0 && scene.yRange()[1] >= 8);
    }

    @Test
    void somethingThatIsNotAPictureIsRefused() {
        assertFalse(GraphicsScene.isGraphics(e("1 + 1")));
        assertThrows(PlotException.class, () -> GraphicsScene.parse(e("1 + 1")));
        assertThrows(PlotException.class, () -> GraphicsScene.parse(e("Graphics([])")));
    }

    // ---- what the algebra knows ---------------------------------------------------------------

    @Test
    void theQueriesAskForWhatTheyClaimTo() {
        assertEquals(
                "solve(Denominator(Together(1/x)) = 0, x)",
                com.calcula.parse.Formatter.format(PlotAnalysis.asymptoteQuery(e("1/x"), "x")));
        assertEquals(
                "solve(deriv(x^2, x) = 0, x)",
                com.calcula.parse.Formatter.format(PlotAnalysis.criticalQuery(e("x^2"), "x")));
    }

    @Test
    void rootsAreReadOutOfSolvesNestedShape() {
        // Solve answers {{x -> -1}, {x -> 1}} — a list of solution SETS, each a list of rules.
        List<Expr> roots = PlotAnalysis.roots(e("[[Rule(x, -1)], [Rule(x, 1)]]"));
        assertEquals(List.of(e("-1"), e("1")), roots);

        // And the flatter shape some heads return.
        assertEquals(List.of(e("2")), PlotAnalysis.roots(e("[Rule(x, 2)]")));
        assertEquals(List.of(), PlotAnalysis.roots(e("[]")));
    }

    @Test
    void polesAndTurningPointsBecomeFeatures() {
        DoubleUnaryOperator f = ExprCompiler.compile(e("x^3 - 3*x"), "x");
        PlotAnalysis analysis = PlotAnalysis.of(List.of(e("2")), List.of(e("-1"), e("1")), f, -10, 10);

        assertEquals(1, analysis.asymptotes().size());
        assertEquals(2.0, analysis.breakPoints()[0], 1e-12);

        assertEquals(2, analysis.criticalPoints().size());
        assertEquals(-1.0, analysis.criticalPoints().get(0).x(), 1e-12);
        assertEquals(2.0, analysis.criticalPoints().get(0).y(), 1e-9);
    }

    @Test
    void aTurningPointIsLabelledWithItsExactCoordinate() {
        // The entire reason for asking the algebra rather than the sampler: a numerical grapher can
        // show you roughly where sin turns; it cannot tell you the turn is at pi/2.
        DoubleUnaryOperator f = ExprCompiler.compile(e("sin(x)"), "x");
        PlotAnalysis analysis = PlotAnalysis.of(List.of(), List.of(e("pi/2")), f, -10, 10);

        assertEquals(1, analysis.criticalPoints().size());
        assertTrue(
                analysis.criticalPoints().get(0).label().startsWith("(pi/2,"),
                analysis.criticalPoints().get(0).label());
    }

    @Test
    void complexRootsAreNotSomewhereToDrawADot() {
        // Solve is happy to return them; a plot of the real line is not.
        DoubleUnaryOperator f = ExprCompiler.compile(e("x^2 + 1"), "x");
        PlotAnalysis analysis = PlotAnalysis.of(List.of(), List.of(e("I"), e("0")), f, -10, 10);
        assertEquals(1, analysis.criticalPoints().size(), "only the real root should be marked");
    }

    @Test
    void rootsOutsideThePlottedRangeAreLeftOut() {
        DoubleUnaryOperator f = ExprCompiler.compile(e("x^2"), "x");
        PlotAnalysis analysis = PlotAnalysis.of(List.of(e("500")), List.of(e("0")), f, -10, 10);
        assertTrue(analysis.asymptotes().isEmpty());
        assertEquals(1, analysis.criticalPoints().size());
    }

    @Test
    void knownPolesBreakTheLineExactlyRatherThanHeuristically() {
        // With the pole known, the break lands on it rather than wherever the jump threshold tripped.
        DoubleUnaryOperator f = ExprCompiler.compile(e("1/(x-2)"), "x");
        Viewport view = new Viewport(0, 4, -10, 10, 200, 200);

        List<Sampler.Segment> withKnowledge = Sampler.sample(f, view, new double[] {2.0});
        assertEquals(2, withKnowledge.size());
        // The first run must stop before the pole and the second start after it.
        Sampler.Segment left = withKnowledge.get(0);
        assertTrue(left.xs()[left.length() - 1] < 2.0, "the left branch should end before the pole");
        assertTrue(withKnowledge.get(1).xs()[0] > 2.0, "the right branch should start after it");
    }

    @Test
    void aKnownBreakDoesNotSplitACurveThatIsFineThere() {
        // A break is only taken where one was asked for.
        DoubleUnaryOperator f = ExprCompiler.compile(e("x"), "x");
        Viewport view = new Viewport(0, 4, -1, 5, 200, 200);
        assertEquals(1, Sampler.sample(f, view, new double[0]).size());
    }
}
