package com.calcula.ui;

import java.util.List;

import javafx.geometry.Rectangle2D;

/**
 * Whether a remembered window position can still be used.
 *
 * <p>Pure, and separate because the decision is the whole feature. Restoring a size is trivial;
 * restoring a <em>position</em> is where a saved layout turns into a window nobody can reach — the
 * monitor it was on is unplugged, the laptop is off the dock, the external display is arranged on the
 * other side — and the failure is total, because a window off every screen cannot be dragged back.
 */
public final class WindowBounds {

    /**
     * How much of the window has to be on a screen for the position to be worth keeping.
     *
     * <p>Enough to grab. A window overlapping by a few pixels is reachable in principle and not in
     * practice, and "in principle" is no use to someone whose calculator has vanished.
     */
    public static final double MIN_VISIBLE_WIDTH = 120;

    public static final double MIN_VISIBLE_HEIGHT = 40;

    /** Below this a window is not a window; a hand-edited file should not be able to produce one. */
    public static final double MIN_WIDTH = 480;

    public static final double MIN_HEIGHT = 320;

    private WindowBounds() {}

    /** True when nothing was saved — a fresh install, or a file from before these keys existed. */
    public static boolean unset(double x, double y) {
        return Double.isNaN(x) || Double.isNaN(y);
    }

    /**
     * True when enough of the window would land on some screen to be grabbed.
     *
     * <p>Takes the screens as rectangles rather than reading them, so the awkward arrangements —
     * a monitor above, a monitor to the left with negative coordinates, one unplugged since — are
     * ordinary test cases rather than something you have to own the hardware to check.
     */
    public static boolean usable(double x, double y, double width, double height, List<Rectangle2D> screens) {
        if (unset(x, y) || width <= 0 || height <= 0 || screens == null || screens.isEmpty()) {
            return false;
        }
        for (Rectangle2D screen : screens) {
            double overlapW = Math.min(x + width, screen.getMaxX()) - Math.max(x, screen.getMinX());
            double overlapH = Math.min(y + height, screen.getMaxY()) - Math.max(y, screen.getMinY());
            if (overlapW >= MIN_VISIBLE_WIDTH && overlapH >= MIN_VISIBLE_HEIGHT) {
                return true;
            }
        }
        return false;
    }
}
