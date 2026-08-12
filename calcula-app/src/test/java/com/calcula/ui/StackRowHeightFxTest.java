package com.calcula.ui;

import java.util.List;

import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Region;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import com.calcula.expr.Expr;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stack row is as tall as the value it holds.
 *
 * <p>Nothing else in the suite asserts this, and the failure is invisible to every test that reads
 * text or style classes: the value is present, correctly rendered and correctly classed — it is
 * simply drawn on top of its neighbours. Only a measurement says so.
 *
 * <p>The trap is that {@code -fx-cell-size} is not a floor. AtlantaFX Primer sets it on
 * {@code .list-view .list-cell}, and {@code ListCellSkin.computePrefHeight} returns that size
 * <b>instead of</b> measuring the content whenever it differs from JavaFX's own default — so a tall
 * value is laid out inside a box sized for one line of text, and paints outside it.
 */
@Tag("fx")
class StackRowHeightFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    /** Identity engine, so what goes in is what lands on the stack. */
    private record StubEngine(String id, String version) implements CasEngine {
        @Override
        public Expr eval(Expr input) throws CasException {
            return input;
        }

        @Override
        public String texForm(Expr input) {
            return "";
        }

        @Override
        public String mathmlForm(Expr input) {
            return "";
        }
    }

    /**
     * Themed on purpose. A bare {@code realize()} loads only Primer, so {@code app.css} — the sheet
     * that restores the cell size — is absent and every assertion here passes against the defect.
     * That is not hypothetical: this test was written with {@code realize()} and was green while a
     * 7-row matrix was overflowing its row by 41px.
     */
    private static CalcWindow open() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realizeThemed(window.getRoot(), Themes.SLAB);
        window.setEngine(new StubEngine("stub", "1.0"));
        return window;
    }

    private static void relayout(CalcWindow window) throws Exception {
        FxTestSupport.runOnFx(() -> {
            window.getRoot().applyCss();
            window.getRoot().layout();
        });
    }

    /** Every realised, non-empty stack cell, paired with the height of the row it was given. */
    private static List<double[]> cellAndContentHeights(CalcWindow window) throws Exception {
        return FxTestSupport.callOnFx(() -> window.getRoot().lookupAll(".stack-view .list-cell").stream()
                .filter(n -> n instanceof ListCell<?> c && c.getGraphic() != null)
                .map(n -> {
                    ListCell<?> cell = (ListCell<?>) n;
                    Node graphic = cell.getGraphic();
                    double content = graphic instanceof Region r ? r.prefHeight(-1) : graphic.prefHeight(-1);
                    return new double[] {cell.getHeight(), content};
                })
                .toList());
    }

    /**
     * Seven rows, because two is not enough to fail.
     *
     * <p>A 2×2 matrix measures about 40px and a pinned row is 42, so it fits by accident and every
     * assertion below passes while the defect is fully present. The shape that shows it is the one
     * the stack actually produces: {@code FactorInteger(2^64 - 1)} returns seven rows.
     */
    private static final String TALL = "[[1,2],[3,4],[5,6],[7,8],[9,10],[11,12],[13,14]]";

    @Test
    void aTallValueGetsARowTallEnoughToHoldIt() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit(TALL));
        FxTestSupport.waitFor("the matrix", 5000, () -> !window.stackContents().isEmpty());
        relayout(window);

        List<double[]> rows = cellAndContentHeights(window);
        assertTrue(!rows.isEmpty(), "no realised stack cell to measure");

        for (double[] row : rows) {
            double cell = row[0];
            double content = row[1];
            assertTrue(
                    cell >= content,
                    "a row must be at least as tall as the value it holds, or the value paints over its "
                            + "neighbours: row " + cell + "px holding " + content + "px of content");
        }

        FxTestSupport.runOnFx(window::dispose);
    }

    /**
     * The same property stated where it is cheapest to read: a matrix row is visibly taller than a
     * plain number's. Without this, restoring the cell size to a constant that merely happens to
     * clear the matrix would pass the assertion above and still be wrong.
     */
    @Test
    void aTallerValueGetsATallerRowThanAShortOne() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit("7"));
        FxTestSupport.waitFor("the number", 5000, () -> window.stackContents().size() == 1);
        relayout(window);
        double shortRow = cellAndContentHeights(window).get(0)[0];

        FxTestSupport.runOnFx(() -> window.submit(TALL));
        FxTestSupport.waitFor("the matrix", 5000, () -> window.stackContents().size() == 2);
        relayout(window);
        double tallRow = cellAndContentHeights(window).stream()
                .mapToDouble(r -> r[0])
                .max()
                .orElse(0);

        assertTrue(
                tallRow > shortRow,
                "a matrix row (" + tallRow + "px) should be taller than a number's (" + shortRow + "px)");

        FxTestSupport.runOnFx(window::dispose);
    }

    /**
     * A plot's entry number is level with the top of the chart.
     *
     * <p>{@code PlotCanvas} is a plain {@code Region}, so it reports
     * {@code BASELINE_OFFSET_SAME_AS_HEIGHT} and a box aligning on the baseline has to reconcile a
     * 200px "baseline" against a 12px one. Measured, that pushes the chart 12px below its own entry
     * number and makes the row 12px taller than it needs to be — small, but it is the row the eye
     * lands on, and the number no longer points at anything.
     *
     * <p>The tolerance is tight on purpose: "in the top half" is satisfied by the defect as well as
     * by the fix, so it asserts nothing. The property is that the two tops <em>coincide</em>.
     */
    @Test
    void aPlotIsLabelledAtItsTop() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit("1/(x - 1)"));
        FxTestSupport.waitFor("the formula", 5000, () -> window.stackContents().size() == 1);
        FxTestSupport.runOnFx(() -> window.run("plot.function"));
        FxTestSupport.waitFor("the plot", 5000, () -> window.stackContents().size() == 2);
        relayout(window);

        double[] offsets = FxTestSupport.callOnFx(() -> {
            for (Node n : window.getRoot().lookupAll(".stack-view .list-cell")) {
                if (!(n instanceof ListCell<?> cell) || !(cell.getGraphic() instanceof Region row)) {
                    continue;
                }
                Node plot = row.lookup(".plot-canvas");
                Node label = row.lookup(".stack-index");
                if (plot == null || label == null) {
                    continue;
                }
                // Both in the row's coordinate space, so the two are directly comparable.
                double plotTop = plot.localToScene(0, 0).getY();
                double plotHeight = plot.getBoundsInLocal().getHeight();
                double labelTop = label.localToScene(0, 0).getY();
                return new double[] {labelTop - plotTop, plotHeight};
            }
            return null;
        });

        assertTrue(offsets != null, "no plot row found to measure");
        double fromTop = offsets[0];
        assertTrue(
                Math.abs(fromTop) <= 2,
                "the entry number and the top of the chart it labels should be level, but they are " + fromTop
                        + "px apart");

        FxTestSupport.runOnFx(window::dispose);
    }
}
