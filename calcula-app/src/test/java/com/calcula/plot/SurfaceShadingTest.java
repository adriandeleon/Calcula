package com.calcula.plot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The light a face catches.
 *
 * <p>Worth testing rather than eyeballing because the failure is so quiet: reverse the cross product
 * and every slope facing the light goes dark while every slope facing away lights up, which turns
 * hills into pits — and the picture that comes out is still a perfectly plausible surface. Nothing
 * about it looks wrong until you compare it with the function.
 */
class SurfaceShadingTest {

    /** A cell of the normalised grid: a corner, one step along x, one step along y. */
    private static double lit(double alongX, double alongY) {
        double d = 0.1;
        return SurfaceShading.lit(new double[] {0, 0, 0}, new double[] {d, 0, alongX}, new double[] {0, d, alongY});
    }

    @Test
    void aSlopeFacingTheLightIsBrighterThanOneFacingAway() {
        double rising = lit(0.2, 0.2);
        double flat = lit(0, 0);
        double falling = lit(-0.2, -0.2);

        assertTrue(rising > flat, "a slope tilted into the light catches more of it: " + rising + " vs " + flat);
        assertTrue(falling < flat, "and one tilted out of it catches less: " + falling + " vs " + flat);
    }

    /** The light comes from above, so a level face is already well lit before any tilt helps it. */
    @Test
    void aLevelFaceIsLitButNotFully() {
        double flat = lit(0, 0);
        assertTrue(flat > SurfaceShading.AMBIENT, "overhead light reaches a level face");
        assertTrue(flat < 1, "but not squarely, or nothing could be brighter than flat ground");
    }

    /**
     * Turned far enough away, a face bottoms out at the ambient term rather than going negative.
     *
     * <p>Unclamped this is where the sign trap surfaces as a crash instead of a wrong picture: a
     * negative share scales to a negative shade index, and the fill is looked up in an array.
     */
    @Test
    void aFaceTurnedAwayBottomsOutAtAmbient() {
        assertEquals(SurfaceShading.AMBIENT, lit(-2, -2), 1e-12);
    }

    /**
     * Three corners in a line describe no plane, so there is no normal to light.
     *
     * <p>It happens for real: a cell on a ridge the sampler lands exactly along. The value only has to
     * be finite — a NaN here reaches the shade lookup as an index and takes the window down.
     */
    @Test
    void aCellWithNoAreaIsHarmless() {
        double degenerate =
                SurfaceShading.lit(new double[] {0, 0, 0}, new double[] {0.1, 0, 0}, new double[] {0.2, 0, 0});
        assertEquals(SurfaceShading.AMBIENT, degenerate, 1e-12);
    }

    /** Every value has to land in the range the shade table is indexed by. */
    @Test
    void everyFaceLandsInRange() {
        for (double x = -3; x <= 3; x += 0.25) {
            for (double y = -3; y <= 3; y += 0.25) {
                double lit = lit(x, y);
                assertTrue(lit >= SurfaceShading.AMBIENT && lit <= 1, "slope (" + x + ", " + y + ") lit " + lit);
            }
        }
    }
}
