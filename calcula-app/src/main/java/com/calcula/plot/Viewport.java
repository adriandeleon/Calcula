package com.calcula.plot;

/**
 * The window onto the plane: which part of the world is shown, and how big the canvas is.
 *
 * <p>Immutable, so pan and zoom return a new one. That keeps the interaction trivially undoable and the
 * arithmetic testable without a canvas — which matters, because coordinate transforms are exactly the
 * kind of code where an inverted sign is invisible until something is drawn upside down.
 *
 * <p>Screen y runs DOWN and world y runs UP, so the y transform is a flip. Forgetting that is the
 * classic plotting bug, and it looks plausible for symmetric functions.
 */
public record Viewport(double xMin, double xMax, double yMin, double yMax, double width, double height) {

    /** How far a single zoom step moves. */
    public static final double ZOOM_STEP = 1.2;

    private static final double MIN_SPAN = 1e-12;
    private static final double MAX_SPAN = 1e12;

    public Viewport {
        if (!(xMax > xMin) || !(yMax > yMin)) {
            throw new IllegalArgumentException("empty viewport: x " + xMin + ".." + xMax + " y " + yMin + ".." + yMax);
        }
        if (!(width > 0) || !(height > 0)) {
            throw new IllegalArgumentException("canvas has no area: " + width + "x" + height);
        }
    }

    /** A default view: the given x range, and a y range with the same scale so circles look round. */
    public static Viewport around(double xMin, double xMax, double width, double height) {
        double span = (xMax - xMin) * height / width;
        return new Viewport(xMin, xMax, -span / 2, span / 2, width, height);
    }

    public double xSpan() {
        return xMax - xMin;
    }

    public double ySpan() {
        return yMax - yMin;
    }

    public double toScreenX(double worldX) {
        return (worldX - xMin) / xSpan() * width;
    }

    /** Note the flip: world y increases upward, screen y increases downward. */
    public double toScreenY(double worldY) {
        return height - (worldY - yMin) / ySpan() * height;
    }

    public double toWorldX(double screenX) {
        return xMin + screenX / width * xSpan();
    }

    public double toWorldY(double screenY) {
        return yMin + (height - screenY) / height * ySpan();
    }

    /** World units per pixel horizontally — the natural sampling step. */
    public double xPerPixel() {
        return xSpan() / width;
    }

    public Viewport resized(double newWidth, double newHeight) {
        return new Viewport(xMin, xMax, yMin, yMax, newWidth, newHeight);
    }

    /** Drag by a screen delta. */
    public Viewport pan(double dxScreen, double dyScreen) {
        double dx = dxScreen / width * xSpan();
        double dy = dyScreen / height * ySpan();
        // Dragging right should move the world right, i.e. show smaller x; dragging down shows larger y.
        return new Viewport(xMin - dx, xMax - dx, yMin + dy, yMax + dy, width, height);
    }

    /**
     * Zoom about a point on screen, so whatever is under the cursor stays under the cursor.
     *
     * @param factor greater than 1 zooms in
     */
    public Viewport zoomAt(double factor, double screenX, double screenY) {
        double anchorX = toWorldX(screenX);
        double anchorY = toWorldY(screenY);
        double newXSpan = clampSpan(xSpan() / factor);
        double newYSpan = clampSpan(ySpan() / factor);
        // Keep the anchor at the same fraction across the view.
        double fx = (anchorX - xMin) / xSpan();
        double fy = (anchorY - yMin) / ySpan();
        double newXMin = anchorX - fx * newXSpan;
        double newYMin = anchorY - fy * newYSpan;
        return new Viewport(newXMin, newXMin + newXSpan, newYMin, newYMin + newYSpan, width, height);
    }

    public Viewport zoom(double factor) {
        return zoomAt(factor, width / 2, height / 2);
    }

    private static double clampSpan(double span) {
        return Math.min(MAX_SPAN, Math.max(MIN_SPAN, span));
    }
}
