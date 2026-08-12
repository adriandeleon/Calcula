package com.calcula.ui;

import javafx.scene.Node;

import com.calcula.parse.Formatter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The strip above the input, wired up.
 *
 * <p>{@link InputPreviewTest} covers what it decides; this covers that the decision reaches the
 * window, on the debounce, and that it takes no room when it has nothing to say.
 */
@Tag("fx")
class InputPreviewFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @BeforeEach
    void isolate() throws Exception {
        FxTestSupport.freshConfigDir();
    }

    private static CalcWindow open() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realizeThemed(window.getRoot(), Themes.SLAB);
        return window;
    }

    @Test
    void typingAnExpressionSetsItAboveTheLine() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.type("1/2 + 1/3"));

        FxTestSupport.waitFor("the parse", 5000, () -> window.previewShown().parsed() != null);
        assertEquals(
                "1/2 + 1/3",
                Formatter.format(window.previewShown().parsed()),
                "the strip should show what the line reads as");

        // And it is really in the scene, not just decided.
        Node strip = FxTestSupport.callOnFx(() -> window.getRoot().lookup(".input-preview"));
        assertNotNull(strip);
        assertTrue(FxTestSupport.callOnFx(strip::isVisible));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aBlankLineTakesNoHeight() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.type("1 + 1"));
        FxTestSupport.waitFor("the parse", 5000, () -> window.previewShown().parsed() != null);

        FxTestSupport.runOnFx(() -> window.type(""));
        FxTestSupport.waitFor(
                "the strip to go quiet", 5000, () -> window.previewShown().isQuiet());

        Node strip = FxTestSupport.callOnFx(() -> window.getRoot().lookup(".input-preview"));
        assertFalse(FxTestSupport.callOnFx(strip::isManaged), "an unmanaged strip costs no height");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aSyntaxErrorIsShownWhereItHappened() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.type("1 +"));

        FxTestSupport.waitFor("the complaint", 5000, () -> window.previewShown().error() != null);
        assertTrue(
                FxTestSupport.callOnFx(() -> window.getRoot().lookup(".input-preview-error") != null),
                "an error should be shown as one, in the colour errors use");
        FxTestSupport.runOnFx(window::dispose);
    }

    /** Nothing is evaluated to draw this, so a line naming an unknown function is still previewed. */
    @Test
    void thePreviewNeverRunsTheEngine() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.type("Frobnicate(3) + 1"));

        FxTestSupport.waitFor("the parse", 5000, () -> window.previewShown().parsed() != null);
        assertTrue(window.stackContents().isEmpty(), "previewing must not put anything on the stack");
        FxTestSupport.runOnFx(window::dispose);
    }
}
