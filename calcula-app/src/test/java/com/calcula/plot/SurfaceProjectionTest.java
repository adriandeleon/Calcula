package com.calcula.plot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a point lands, asserted as numbers.
 *
 * <p>This is the part of drawing a surface that is easy to get subtly wrong and hard to see wrong — a
 * picture that is inside out or mirrored looks like a picture. So the projection is checked by
 * arithmetic on the views whose answers are known by inspection.
 */
class SurfaceProjectionTest {

    private static final double EPS = 1e-9;
    private static final double LOOSE = 1e-6;

    /** A unit cube on all three axes, so normalisation is the identity and numbers stay readable. */
    private static SurfaceProjection view(double azimuth, double elevation) {
        return new SurfaceProjection(azimuth, elevation, 1, -1, 1, -1, 1, -1, 1, 200, 200);
    }

    @Test
    void theCentreOfTheDataIsTheCentreOfTheCanvas() {
        double[] p = view(0.7, 0.5).screen(0, 0, 0);
        assertEquals(100, p[0], EPS);
        assertEquals(100, p[1], EPS);
    }

    /**
     * Looking from above, height all but disappears: the picture becomes the xy plane.
     *
     * <p>Stated as a ratio rather than as zero, because the top of the range is deliberately a
     * fraction short of straight down — the degenerate view has no orientation left to rotate from,
     * so it is excluded rather than reached. Two points differing only in height are about two pixels
     * apart there and a hundred and seventy apart edge-on, and it is that collapse being asserted.
     */
    @Test
    void fromAboveHeightAllButDisappears() {
        double fromAbove = heightSeparation(view(0, SurfaceProjection.MAX_ELEVATION));
        double fromTheSide = heightSeparation(view(0, SurfaceProjection.MIN_ELEVATION));

        assertTrue(fromAbove < fromTheSide / 20, "from above: " + fromAbove + ", from the side: " + fromTheSide);
        // And sideways it does nothing at all: height never moves a point left or right.
        SurfaceProjection top = view(0, SurfaceProjection.MAX_ELEVATION);
        assertEquals(top.screen(0.5, 0.5, -1)[0], top.screen(0.5, 0.5, 1)[0], EPS);
    }

    /** How far apart two points differing only in height end up on screen. */
    private static double heightSeparation(SurfaceProjection p) {
        return Math.abs(p.screen(0.5, 0.5, 1)[1] - p.screen(0.5, 0.5, -1)[1]);
    }

    /** Edge on, height is the whole picture: z runs straight up the screen and y is invisible. */
    @Test
    void fromTheSideHeightRunsUpTheScreen() {
        SurfaceProjection side = view(0, SurfaceProjection.MIN_ELEVATION);
        double[] low = side.screen(0, 0, -1);
        double[] high = side.screen(0, 0, 1);
        assertTrue(high[1] < low[1], "greater height must be higher on screen, where y grows downward");

        double[] near = side.screen(0, -1, 0);
        double[] far = side.screen(0, 1, 0);
        assertEquals(near[0], far[0], LOOSE, "y is edge-on, so it moves nothing sideways");
    }

    /** The sign that decides whether the picture is inside out. */
    @Test
    void greaterHeightIsAlwaysHigherOnScreen() {
        for (double e = 0.1; e < Math.PI / 2 - 0.1; e += 0.2) {
            SurfaceProjection p = view(0.4, e);
            assertTrue(p.screen(0, 0, 1)[1] < p.screen(0, 0, -1)[1], "at elevation " + e);
        }
    }

    @Test
    void greaterXIsFurtherRightWhenFacingIt() {
        SurfaceProjection front = view(0, 0.5);
        assertTrue(front.screen(1, 0, 0)[0] > front.screen(-1, 0, 0)[0]);
    }

    // ---- depth -------------------------------------------------------------------------------

    /** Height is what leans toward the viewer as the camera rises. */
    @Test
    void fromAboveAHigherPointIsNearer() {
        SurfaceProjection top = view(0, SurfaceProjection.MAX_ELEVATION);
        assertTrue(top.depth(0, 0, 1) > top.depth(0, 0, -1));
    }

    /** Edge on, it is y that runs into the screen instead. */
    @Test
    void fromTheSideTheNearerYIsNearer() {
        SurfaceProjection side = view(0, SurfaceProjection.MIN_ELEVATION);
        assertTrue(side.depth(0, 1, 0) > side.depth(0, -1, 0));
    }

    /** Turning half a circle swaps which side of the surface is toward you. */
    @Test
    void turningAroundReversesTheOrder() {
        SurfaceProjection front = view(0, 0.5);
        SurfaceProjection behind = front.turned(Math.PI, 0);
        assertTrue(front.depth(0, 1, 0) > front.depth(0, -1, 0));
        assertTrue(behind.depth(0, 1, 0) < behind.depth(0, -1, 0));
    }

    // ---- the property that stops the picture breathing ----------------------------------------

    /**
     * The scale must not depend on the angle. Measuring a bounding box per frame is the obvious way
     * to fit the picture and makes the surface swell every time a corner turns to face you.
     */
    @Test
    void theScaleDoesNotChangeAsItTurns() {
        double at = view(0, 0.5).scale();
        for (double a = 0; a < 2 * Math.PI; a += 0.3) {
            assertEquals(at, view(a, 0.5).scale(), EPS, "azimuth " + a);
        }
    }

    /** And nothing may leave the canvas at any angle, which is what the cube radius buys. */
    @Test
    void everyCornerStaysOnTheCanvasAtEveryAngle() {
        for (double a = 0; a < 2 * Math.PI; a += 0.25) {
            for (double e = 0.05; e < Math.PI / 2; e += 0.25) {
                SurfaceProjection p = view(a, e);
                for (double x = -1; x <= 1; x += 2) {
                    for (double y = -1; y <= 1; y += 2) {
                        for (double z = -1; z <= 1; z += 2) {
                            double[] s = p.screen(x, y, z);
                            assertTrue(
                                    s[0] >= 0 && s[0] <= 200 && s[1] >= 0 && s[1] <= 200,
                                    "corner off canvas at azimuth " + a + " elevation " + e);
                        }
                    }
                }
            }
        }
    }

    // ---- normalising --------------------------------------------------------------------------

    /**
     * Each axis by its own range. A height running to millions over an x of a few units is a shape,
     * not a spike — the axes are not comparable quantities and pretending they are draws a wall.
     */
    @Test
    void eachAxisIsScaledByItsOwnRange() {
        SurfaceProjection tall = new SurfaceProjection(0, 0.5, 1, -3, 3, -3, 3, 0, 1_000_000, 200, 200);
        double[] bottom = tall.screen(0, 0, 0);
        double[] top = tall.screen(0, 0, 1_000_000);
        SurfaceProjection square = view(0, 0.5);

        double tallSpan = Math.abs(top[1] - bottom[1]);
        double squareSpan = Math.abs(square.screen(0, 0, 1)[1] - square.screen(0, 0, -1)[1]);
        assertEquals(squareSpan, tallSpan, LOOSE, "the same shape, whatever the units");
    }

    @Test
    void aFlatSurfaceSitsInTheMiddleRatherThanDividingByZero() {
        SurfaceProjection flat = new SurfaceProjection(0.3, 0.5, 1, -1, 1, -1, 1, 5, 5, 200, 200);
        double[] p = flat.screen(0, 0, 5);
        assertTrue(Double.isFinite(p[0]) && Double.isFinite(p[1]));
    }

    // ---- limits ------------------------------------------------------------------------------

    @Test
    void elevationAndZoomAreHeldWithinTheUsefulRange() {
        assertEquals(SurfaceProjection.MAX_ELEVATION, view(0, 10).elevation(), EPS);
        assertEquals(SurfaceProjection.MIN_ELEVATION, view(0, -10).elevation(), EPS);
        assertEquals(SurfaceProjection.MAX_ZOOM, view(0, 0.5).zoomedBy(1000).zoom(), EPS);
        assertEquals(SurfaceProjection.MIN_ZOOM, view(0, 0.5).zoomedBy(0.0001).zoom(), EPS);
    }

    @Test
    void aGridGivesAViewOfItself() {
        SurfaceSampler.Grid g = SurfaceSampler.sample((x, y) -> x * x + y * y, -2, 2, -2, 2, 5);
        SurfaceProjection p = SurfaceProjection.of(g, 300, 200);
        assertEquals(-2, p.xMin(), EPS);
        assertEquals(2, p.yMax(), EPS);
        assertEquals(0, p.zMin(), EPS);
        assertEquals(8, p.zMax(), EPS);
    }

    /** A surface with nothing finite in it still has to produce a usable view. */
    @Test
    void anEmptySurfaceStillProjects() {
        SurfaceSampler.Grid g = SurfaceSampler.sample((x, y) -> Double.NaN, -1, 1, -1, 1, 4);
        SurfaceProjection p = SurfaceProjection.of(g, 200, 200);
        double[] s = p.screen(0, 0, 0);
        assertTrue(Double.isFinite(s[0]) && Double.isFinite(s[1]));
    }
}
