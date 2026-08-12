package com.calcula.ui;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.Region;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import com.calcula.expr.Expr;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A long trail line wraps instead of running off the side.
 *
 * <p>The reported window cut {@code FactorInteger}'s result off mid-token at {@code "[65"}, behind a
 * horizontal scrollbar. A log you have to scroll sideways is a log you do not read, and this one is
 * meant to be the plain-text record of the session.
 */
@Tag("fx")
class TrailWrapFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
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

    /** Wider than the trail column by a long way, and the shape a real factorisation has. */
    private static final String LONG = "[[3, 1], [5, 1], [17, 1], [257, 1], [641, 1], [65537, 1], [6700417, 1]]";

    private static CalcWindow open() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realizeThemed(window.getRoot(), Themes.SLAB);
        window.setEngine(new StubEngine("stub", "1.0"));
        return window;
    }

    @Test
    void aLineTooWideForTheColumnWrapsRatherThanScrolls() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit(LONG));
        FxTestSupport.waitFor("the result", 5000, () -> window.trailContents().size() >= 2);
        FxTestSupport.runOnFx(() -> {
            window.getRoot().applyCss();
            window.getRoot().layout();
        });

        boolean scrollsSideways = FxTestSupport.callOnFx(() -> {
            Region trail = (Region) window.getRoot().lookup(".trail-view");
            for (Node n : trail.lookupAll(".scroll-bar")) {
                if (n instanceof ScrollBar bar && bar.getOrientation() == Orientation.HORIZONTAL && bar.isVisible()) {
                    return true;
                }
            }
            return false;
        });
        assertFalse(scrollsSideways, "the trail should wrap, not scroll sideways");

        // And the wrap is real: the text label took more than one line's height.
        boolean wrapped = FxTestSupport.callOnFx(() -> {
            Region trail = (Region) window.getRoot().lookup(".trail-view");
            for (Node n : trail.lookupAll(".list-cell")) {
                if (!(n instanceof ListCell<?> cell) || !(cell.getGraphic() instanceof Region row)) {
                    continue;
                }
                for (Node inner : row.getChildrenUnmodifiable()) {
                    if (inner instanceof Label label
                            && label.isWrapText()
                            && label.getHeight() > label.getFont().getSize() * 1.6) {
                        return true;
                    }
                }
            }
            return false;
        });
        assertTrue(wrapped, "a line this long should have taken more than one row of text");
        FxTestSupport.runOnFx(window::dispose);
    }

    /**
     * The sigil keeps its own column, which is the point of splitting it out: a wrapped line resumes
     * under the text, not under the "= ".
     */
    @Test
    void theSigilStaysInItsOwnColumn() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit(LONG));
        FxTestSupport.waitFor("the result", 5000, () -> window.trailContents().size() >= 2);
        FxTestSupport.runOnFx(() -> {
            window.getRoot().applyCss();
            window.getRoot().layout();
        });

        boolean hangingIndent = FxTestSupport.callOnFx(() -> {
            Region trail = (Region) window.getRoot().lookup(".trail-view");
            for (Node n : trail.lookupAll(".trail-sigil")) {
                if (n instanceof Label sigil && sigil.getText().contains("=")) {
                    return sigil.getWidth() > 0;
                }
            }
            return false;
        });
        assertTrue(hangingIndent, "the result sigil should occupy a column of its own");

        // The rendered string is still the whole line, so the record and the tests are unchanged.
        assertTrue(
                window.trailContents().stream().anyMatch(l -> l.startsWith("  = ")),
                "renderTrail must still produce the full line: " + window.trailContents());
        FxTestSupport.runOnFx(window::dispose);
    }
}
