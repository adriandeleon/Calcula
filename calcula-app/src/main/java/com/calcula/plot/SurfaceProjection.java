package com.calcula.plot;

/**
 * Where a point of a surface lands on a flat canvas, and how near the viewer it is.
 *
 * <p>Pure, and no JavaFX: a projection is arithmetic, and keeping it out of the canvas means the part
 * that is easy to get subtly wrong can be checked by asserting numbers rather than by looking at a
 * picture and deciding whether it seems right.
 *
 * <p>Orthographic rather than perspective. A surface plot is read for the <em>shape</em> of a
 * function, and perspective makes equal steps in x cover unequal distances on screen — which is
 * exactly the thing being measured by eye. Every graphing package draws these flat for the same
 * reason.
 *
 * @param azimuth turn about the vertical axis, radians
 * @param elevation angle above the horizontal plane: 0 is edge-on, π/2 is straight down
 */
public record SurfaceProjection(
        double azimuth,
        double elevation,
        double zoom,
        double xMin,
        double xMax,
        double yMin,
        double yMax,
        double zMin,
        double zMax,
        double width,
        double height) {

    /** A three-quarter view, which shows a surface has three dimensions without labouring it. */
    public static final double DEFAULT_AZIMUTH = -Math.PI / 5;

    public static final double DEFAULT_ELEVATION = Math.PI / 6;

    /** Straight down and edge-on are the ends of the useful range; past them the picture inverts. */
    public static final double MIN_ELEVATION = 0.02;

    public static final double MAX_ELEVATION = Math.PI / 2 - 0.02;

    public static final double MIN_ZOOM = 0.2;

    public static final double MAX_ZOOM = 8;

    /**
     * Half the diagonal of the unit cube the data is normalised into.
     *
     * <p>Scaling by this rather than by the projected extent is what stops the picture breathing as
     * it turns: a bounding box measured per frame grows and shrinks with the angle, so the surface
     * would swell every time a corner came round to face you.
     */
    private static final double CUBE_RADIUS = Math.sqrt(3) / 2;

    /** Room for the picture not to touch the edge of its own canvas. */
    private static final double MARGIN = 0.86;

    public static SurfaceProjection of(SurfaceSampler.Grid grid, double width, double height) {
        double[] z = grid.zRange();
        // A surface with no finite point anywhere still has to produce a usable projection rather
        // than dividing by an empty range; there is simply nothing to draw into it.
        double zLo = Double.isNaN(z[0]) ? 0 : z[0];
        double zHi = Double.isNaN(z[1]) || z[1] == zLo ? zLo + 1 : z[1];
        return new SurfaceProjection(
                DEFAULT_AZIMUTH,
                DEFAULT_ELEVATION,
                1,
                grid.xs()[0],
                grid.xs()[grid.steps() - 1],
                grid.ys()[0],
                grid.ys()[grid.steps() - 1],
                zLo,
                zHi,
                width,
                height);
    }

    public SurfaceProjection {
        elevation = Math.clamp(elevation, MIN_ELEVATION, MAX_ELEVATION);
        zoom = Math.clamp(zoom, MIN_ZOOM, MAX_ZOOM);
    }

    /**
     * Where {@code (x, y, z)} lands, as {@code {screenX, screenY}}.
     *
     * <p>Each axis is normalised by its own range first, so a function whose height runs to millions
     * over an x of ±3 is still a shape rather than a spike. The axes are not comparable quantities and
     * pretending they are would draw almost everything as a wall.
     */
    public double[] screen(double x, double y, double z) {
        double nx = norm(x, xMin, xMax);
        double ny = norm(y, yMin, yMax);
        double nz = norm(z, zMin, zMax);

        double cosA = Math.cos(azimuth);
        double sinA = Math.sin(azimuth);
        double x1 = nx * cosA - ny * sinA;
        double y1 = nx * sinA + ny * cosA;

        double cosE = Math.cos(elevation);
        double sinE = Math.sin(elevation);
        // Screen "up" is height tilted by the elevation, and the y axis leaning in behind it.
        double up = nz * cosE - y1 * sinE;

        double scale = scale();
        return new double[] {width / 2 + x1 * scale, height / 2 - up * scale};
    }

    /**
     * How near the viewer a point is. Larger is nearer.
     *
     * <p>Only ever compared, never measured, which is why it is a bare number rather than a distance:
     * quads are drawn in order of it, and the order is the whole of what a painter's algorithm needs.
     */
    public double depth(double x, double y, double z) {
        double nx = norm(x, xMin, xMax);
        double ny = norm(y, yMin, yMax);
        double nz = norm(z, zMin, zMax);
        double y1 = nx * Math.sin(azimuth) + ny * Math.cos(azimuth);
        return y1 * Math.cos(elevation) + nz * Math.sin(elevation);
    }

    public double scale() {
        return Math.min(width, height) * MARGIN * zoom / (2 * CUBE_RADIUS);
    }

    public SurfaceProjection turned(double dAzimuth, double dElevation) {
        return new SurfaceProjection(
                azimuth + dAzimuth, elevation + dElevation, zoom, xMin, xMax, yMin, yMax, zMin, zMax, width, height);
    }

    public SurfaceProjection zoomedBy(double factor) {
        return new SurfaceProjection(
                azimuth, elevation, zoom * factor, xMin, xMax, yMin, yMax, zMin, zMax, width, height);
    }

    public SurfaceProjection resized(double newWidth, double newHeight) {
        return new SurfaceProjection(azimuth, elevation, zoom, xMin, xMax, yMin, yMax, zMin, zMax, newWidth, newHeight);
    }

    /** Into [-0.5, 0.5]. A zero-width range is a flat surface, which sits in the middle. */
    private static double norm(double value, double min, double max) {
        double span = max - min;
        return span == 0 ? 0 : (value - min) / span - 0.5;
    }
}
