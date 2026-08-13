package com.calcula.ui;

import javafx.scene.control.SplitPane;

import com.calcula.config.Settings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The window remembers how it was left.
 *
 * <p>The divider was set once at construction and never written down, so dragging it and restarting
 * put it back at 0.28 — and there was no command to close the trail at all, in an application
 * otherwise shaped like Emacs. A window whose proportions are a constant fits one kind of work.
 */
@Tag("fx")
class TrailLayoutFxTest {

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

    private static SplitPane splitOf(CalcWindow window) throws Exception {
        return FxTestSupport.callOnFx(() -> (SplitPane) window.getRoot().lookup(".split-pane"));
    }

    @Test
    void theTrailStartsAtItsRememberedWidth() throws Exception {
        CalcWindow first = open();
        FxTestSupport.waitFor(
                "the divider to settle",
                5000,
                () -> !splitOf(first).getDividers().isEmpty());
        SplitPane firstSplit = splitOf(first);
        FxTestSupport.runOnFx(() -> firstSplit.setDividerPositions(0.45));
        FxTestSupport.waitFor(
                "the move to be saved", 5000, () -> first.settings().trailSplit() > 0.4);
        FxTestSupport.runOnFx(first::dispose);

        CalcWindow second = open();
        assertEquals(
                first.settings().trailSplit(),
                second.settings().trailSplit(),
                0.001,
                "a dragged divider should still be there next time");
        FxTestSupport.runOnFx(second::dispose);
    }

    @Test
    void theTrailCanBeClosedAndReopened() throws Exception {
        CalcWindow window = open();
        assertEquals(2, splitOf(window).getItems().size(), "both panes to start");

        FxTestSupport.runOnFx(() -> window.run("view.trail"));
        assertEquals(1, splitOf(window).getItems().size(), "a closed trail leaves the split entirely");
        assertFalse(window.settings().trailShown());

        FxTestSupport.runOnFx(() -> window.run("view.trail"));
        assertEquals(2, splitOf(window).getItems().size());
        assertTrue(window.settings().trailShown());
        FxTestSupport.runOnFx(window::dispose);
    }

    /** Closing must not forget the width, or reopening quietly undoes a choice already made. */
    @Test
    void closingTheTrailKeepsTheWidthItHad() throws Exception {
        CalcWindow window = open();
        FxTestSupport.waitFor(
                "the divider to settle",
                5000,
                () -> !splitOf(window).getDividers().isEmpty());
        SplitPane pane = splitOf(window);
        FxTestSupport.runOnFx(() -> pane.setDividerPositions(0.42));
        FxTestSupport.waitFor(
                "the move to be saved", 5000, () -> window.settings().trailSplit() > 0.4);
        double wanted = window.settings().trailSplit();

        FxTestSupport.runOnFx(() -> window.run("view.trail"));
        FxTestSupport.runOnFx(() -> window.run("view.trail"));
        FxTestSupport.waitFor(
                "the divider back", 5000, () -> !splitOf(window).getDividers().isEmpty());

        assertEquals(wanted, window.settings().trailSplit(), 0.001, "the width survived the round trip");
        FxTestSupport.runOnFx(window::dispose);
    }

    /** A closed trail is a decision, so it has to outlive the session that made it. */
    @Test
    void aClosedTrailStaysClosedNextTime() throws Exception {
        CalcWindow first = open();
        FxTestSupport.runOnFx(() -> first.run("view.trail"));
        FxTestSupport.runOnFx(first::dispose);

        CalcWindow second = open();
        assertFalse(second.settings().trailShown());
        assertEquals(1, splitOf(second).getItems().size(), "and it opens without the column");
        FxTestSupport.runOnFx(second::dispose);
    }

    @Test
    void aHandEditedWidthCannotHideTheStack() throws Exception {
        assertEquals(
                Settings.MAX_TRAIL_SPLIT,
                Settings.DEFAULTS.withTrailSplit(0.99).trailSplit(),
                0.001,
                "clamped rather than rejected, like every other size here");
        assertEquals(
                Settings.MIN_TRAIL_SPLIT, Settings.DEFAULTS.withTrailSplit(0.0).trailSplit(), 0.001);
    }
}
