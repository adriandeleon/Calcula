package com.calcula.ui;

import java.util.List;

import javafx.geometry.Rectangle2D;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a remembered position can still be used.
 *
 * <p>The screens are passed in rather than read, which is what makes the awkward arrangements
 * ordinary test cases instead of something you need the hardware to check: a monitor above, one to
 * the left with negative coordinates, and — the case that matters — one that is no longer there.
 */
class WindowBoundsTest {

    private static final Rectangle2D LAPTOP = new Rectangle2D(0, 0, 1440, 900);

    /** A second display arranged to the LEFT, so its coordinates are negative. */
    private static final Rectangle2D LEFT_OF_IT = new Rectangle2D(-1920, -200, 1920, 1080);

    @Test
    void aWindowOnTheOnlyScreenIsUsable() {
        assertTrue(WindowBounds.usable(100, 100, 980, 660, List.of(LAPTOP)));
    }

    @Test
    void nothingSavedIsNotUsable() {
        assertFalse(WindowBounds.usable(Double.NaN, Double.NaN, 980, 660, List.of(LAPTOP)));
        assertTrue(WindowBounds.unset(Double.NaN, 0));
        assertTrue(WindowBounds.unset(0, Double.NaN));
        assertFalse(WindowBounds.unset(0, 0), "the top-left corner of a screen is a real position");
    }

    /** Negative is a legitimate position, not a broken one. */
    @Test
    void aWindowOnADisplayArrangedToTheLeftIsUsable() {
        assertTrue(WindowBounds.usable(-1800, -100, 980, 660, List.of(LAPTOP, LEFT_OF_IT)));
    }

    /**
     * The case the whole thing exists for. The window was on the second display; the second display
     * is gone. Restoring the position would open the calculator somewhere with nothing to grab.
     */
    @Test
    void aPositionOnAScreenThatIsNoLongerThereIsNotUsable() {
        assertTrue(WindowBounds.usable(-1800, -100, 980, 660, List.of(LAPTOP, LEFT_OF_IT)));
        assertFalse(WindowBounds.usable(-1800, -100, 980, 660, List.of(LAPTOP)), "the monitor was unplugged");
    }

    @Test
    void aWindowWithOnlyASliverOnScreenIsNotWorthKeeping() {
        // Ten pixels of overlap is reachable in principle and not in practice.
        assertFalse(WindowBounds.usable(1430, 400, 980, 660, List.of(LAPTOP)));
    }

    @Test
    void aWindowStraddlingTwoScreensIsUsable() {
        assertTrue(WindowBounds.usable(-200, 100, 980, 660, List.of(LAPTOP, LEFT_OF_IT)));
    }

    @Test
    void noScreensAtAllIsNotUsable() {
        assertFalse(WindowBounds.usable(0, 0, 980, 660, List.of()));
        assertFalse(WindowBounds.usable(0, 0, 980, 660, null));
    }
}
