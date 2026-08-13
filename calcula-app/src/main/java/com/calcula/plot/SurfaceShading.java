package com.calcula.plot;

/**
 * How much light a piece of surface catches.
 *
 * <p>Pure arithmetic, and deliberately outside the canvas: shading is the part of a surface plot
 * most easily got subtly wrong — a sign slip turns every peak into a pit and the picture still looks
 * entirely plausible — so it is worth being able to assert numbers about rather than squinting at a
 * drawing and deciding whether it seems right.
 *
 * <p><b>The light is fixed in the data's own space, not the camera's.</b> Turning a surface is
 * already a hand turning an object on a table, and a light bolted to the camera relights every face
 * as you turn, so no slope keeps its brightness and the shape dissolves. Fixed, a slope that faces
 * the light stays bright as it comes round — which is the cue that reads as three dimensions.
 */
public final class SurfaceShading {

    /**
     * What a face turned fully away still gets.
     *
     * <p>Not zero. With no ambient term the far side of a hill goes to flat black, and the mesh drawn
     * over it vanishes into the fill — so the picture loses exactly the detail the fill was added to
     * make legible.
     */
    public static final double AMBIENT = 0.38;

    /** From above, and off to the near left of the default view, roughly where a desk lamp is. */
    private static final double[] LIGHT = unit(-0.40, -0.52, 0.76);

    private SurfaceShading() {}

    /**
     * The share of full light the plane through three corners catches, in {@code [AMBIENT, 1]}.
     *
     * <p>Corners must be in the normalised cube rather than in the data's own units. An x measured in
     * thousands beside a z measured in tenths gives every cell a normal pointing along x, and the
     * whole surface shades as a single flat wall — the same reason {@link SurfaceProjection} normalises
     * before it projects.
     */
    public static double lit(double[] a, double[] b, double[] c) {
        double ux = b[0] - a[0];
        double uy = b[1] - a[1];
        double uz = b[2] - a[2];
        double vx = c[0] - a[0];
        double vy = c[1] - a[1];
        double vz = c[2] - a[2];

        double nx = uy * vz - uz * vy;
        double ny = uz * vx - ux * vz;
        double nz = ux * vy - uy * vx;

        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        // Three corners in a line have no plane and so no normal. A cell that thin covers no pixels
        // either, so the value only has to be harmless rather than meaningful.
        if (!(length > 0) || !Double.isFinite(length)) {
            return AMBIENT;
        }

        double facing = (nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]) / length;
        return AMBIENT + (1 - AMBIENT) * Math.clamp(facing, 0, 1);
    }

    private static double[] unit(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / length, y / length, z / length};
    }
}
