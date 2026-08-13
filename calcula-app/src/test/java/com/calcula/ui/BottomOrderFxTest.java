package com.calcula.ui;

import javafx.geometry.Bounds;
import javafx.scene.Node;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What sits under what, along the bottom of the window.
 *
 * <p>The stack's entry {@code 1:} and the line being typed into it are a single conversation, and the
 * mode line used to sit between them. Asserted by measuring rather than by reading the constructor,
 * because the order that matters is the one on screen.
 */
@Tag("fx")
class BottomOrderFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @BeforeEach
    void isolate() throws Exception {
        FxTestSupport.freshConfigDir();
    }

    private static double topOf(CalcWindow window, String selector) throws Exception {
        return FxTestSupport.callOnFx(() -> {
            Node n = window.getRoot().lookup(selector);
            Bounds b = n.localToScene(n.getBoundsInLocal());
            return b.getMinY();
        });
    }

    @Test
    void theStatusStripIsAtTheEdgeAndTheInputIsAgainstTheStack() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realizeThemed(window.getRoot(), Themes.SLAB);
        FxTestSupport.runOnFx(() -> {
            window.getRoot().applyCss();
            window.getRoot().layout();
        });

        double stack = topOf(window, ".stack-view");
        double echo = topOf(window, ".echo-area");
        double modes = topOf(window, ".mode-line");

        assertTrue(echo > stack, "the input belongs below the stack");
        assertTrue(
                modes > echo,
                "the status strip belongs at the frame edge, below the input — it was between the "
                        + "stack and the input, splitting the pair that form one conversation");
        FxTestSupport.runOnFx(window::dispose);
    }

    /** The reading of the line sits directly above the line, with nothing in between. */
    @Test
    void theParseIsAdjacentToWhatItParses() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realizeThemed(window.getRoot(), Themes.SLAB);
        FxTestSupport.runOnFx(() -> window.type("1/2 + 1/3"));
        FxTestSupport.waitFor("the parse", 5000, () -> window.previewShown().parsed() != null);
        FxTestSupport.runOnFx(() -> {
            window.getRoot().applyCss();
            window.getRoot().layout();
        });

        double preview = topOf(window, ".input-preview");
        double echo = topOf(window, ".echo-area");
        double modes = topOf(window, ".mode-line");

        assertTrue(preview < echo, "the reading goes above the line it is a reading of");
        assertTrue(preview > topOf(window, ".stack-view"), "and below the stack");
        assertTrue(modes > echo, "with the status strip still at the edge");
        FxTestSupport.runOnFx(window::dispose);
    }
}
