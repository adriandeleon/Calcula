package com.calcula.ui;

import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Region;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import com.calcula.expr.Expr;
import com.calcula.plot.SurfaceValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A surface on the stack, and the three things about a row that a new kind of picture breaks.
 *
 * <p>Two of these are defects that were fixed once already and are re-broken by any picture the code
 * does not know about, which is why they are asserted rather than assumed.
 */
@Tag("fx")
class SurfaceRowFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @BeforeEach
    void isolate() throws Exception {
        FxTestSupport.freshConfigDir();
    }

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

    private static CalcWindow withSurface() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realizeThemed(window.getRoot(), Themes.SLAB);
        window.setEngine(new StubEngine("stub", "1.0"));
        FxTestSupport.runOnFx(() -> window.submit("x^2 + y^2"));
        FxTestSupport.waitFor("the formula", 5000, () -> !window.stackContents().isEmpty());
        FxTestSupport.runOnFx(() -> window.run("plot.surface"));
        FxTestSupport.waitFor("the surface", 10000, () -> window.stackContents().size() == 2);
        FxTestSupport.runOnFx(() -> {
            window.getRoot().applyCss();
            window.getRoot().layout();
        });
        return window;
    }

    @Test
    void aSurfaceLandsOnTheStackAndIsDrawn() throws Exception {
        CalcWindow window = withSurface();
        assertTrue(SurfaceValue.isSurface(window.stackContents().get(1)));
        assertNotNull(
                FxTestSupport.callOnFx(() -> window.getRoot().lookup(".surface-canvas")),
                "the surface should be drawn, not typeset");
        FxTestSupport.runOnFx(window::dispose);
    }

    /** The formula is not consumed: graphing something should not take it away. */
    @Test
    void theFormulaStaysOnTheStack() throws Exception {
        CalcWindow window = withSurface();
        assertEquals(2, window.stackContents().size());
        FxTestSupport.runOnFx(window::dispose);
    }

    /**
     * Trap one. The rail judges a picture by what it draws rather than by the numbers saying where it
     * was cropped — a surface has four such bounds, so forgetting it marks every surface approximate.
     */
    @Test
    void aSurfaceOfAnExactFormulaWearsNoMarker() throws Exception {
        CalcWindow window = withSurface();
        assertFalse(
                FxTestSupport.callOnFx(() -> window.getRoot().lookupAll(".stack-gutter").stream()
                        .anyMatch(n -> n.getStyleClass().contains("inexact"))),
                "x^2 + y^2 is exact, and the range it is drawn over says nothing about that");
        FxTestSupport.runOnFx(window::dispose);
    }

    /**
     * Trap two. A picture has no baseline, so its entry number goes at its top; a picture the cell
     * does not recognise as one goes back to being aligned against the bottom of a 300px canvas.
     */
    @Test
    void theEntryNumberIsLevelWithTheTopOfTheSurface() throws Exception {
        CalcWindow window = withSurface();
        double[] tops = FxTestSupport.callOnFx(() -> {
            for (Node n : window.getRoot().lookupAll(".stack-view .list-cell")) {
                if (n instanceof ListCell<?> cell && cell.getGraphic() instanceof Region row) {
                    Node canvas = row.lookup(".surface-canvas");
                    Node index = row.lookup(".stack-index");
                    if (canvas != null && index != null) {
                        return new double[] {
                            canvas.localToScene(0, 0).getY(),
                            index.localToScene(0, 0).getY()
                        };
                    }
                }
            }
            return null;
        });
        assertNotNull(tops, "no surface row found");
        assertEquals(tops[0], tops[1], 2, "the number labels the block, so it sits at its top");
        FxTestSupport.runOnFx(window::dispose);
    }

    /** Trap three. Scrolling past a surface must not re-run the function 3,600 times. */
    @Test
    void theGridIsSampledOnceAndKept() throws Exception {
        CalcWindow window = withSurface();
        Object first = FxTestSupport.callOnFx(
                () -> window.gridFor(window.stackContents().get(1)));
        FxTestSupport.runOnFx(() -> {
            window.getRoot().applyCss();
            window.getRoot().layout();
        });
        Object again = FxTestSupport.callOnFx(
                () -> window.gridFor(window.stackContents().get(1)));
        assertTrue(first == again, "the same grid, not a fresh sample");
        FxTestSupport.runOnFx(window::dispose);
    }

    /** A formula of one variable is a curve, and says so rather than drawing something wrong. */
    @Test
    void aFormulaWithTheWrongNumberOfVariablesSaysSo() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realizeThemed(window.getRoot(), Themes.SLAB);
        window.setEngine(new StubEngine("stub", "1.0"));
        FxTestSupport.runOnFx(() -> window.submit("x^2"));
        FxTestSupport.waitFor("the formula", 5000, () -> !window.stackContents().isEmpty());
        FxTestSupport.runOnFx(() -> window.run("plot.surface"));

        FxTestSupport.waitFor(
                "the complaint",
                5000,
                () -> window.trailContents().stream().anyMatch(l -> l.contains("two variables")));
        assertEquals(1, window.stackContents().size(), "and nothing was pushed");
        FxTestSupport.runOnFx(window::dispose);
    }
}
