package com.calcula.ui.plot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How many of a grid's lines the wireframe actually draws.
 *
 * <p>Pure, and no toolkit: this is arithmetic, and one of its answers takes the window down.
 */
class SurfaceWireframeTest {

    /**
     * A stride of zero is a crash, not a dense wireframe.
     *
     * <p>It is used as {@code index % stride}, so zero throws {@link ArithmeticException} out of the
     * paint loop — on the FX thread, for every frame, on any surface small enough to round down. The
     * clamp is the whole reason this is a named method rather than an expression inlined at the one
     * place it is used.
     */
    @Test
    void aStrideIsNeverZero() {
        for (int steps = 0; steps <= 40; steps++) {
            assertTrue(SurfaceCanvas.wireframeStride(steps) >= 1, "steps " + steps);
        }
    }

    /** A grid already coarser than the target keeps every line it has. */
    @Test
    void aCoarseGridIsDrawnWhole() {
        assertEquals(1, SurfaceCanvas.wireframeStride(8));
        assertEquals(1, SurfaceCanvas.wireframeStride(21));
    }

    /**
     * However finely a surface is sampled, the wireframe stays about the same density.
     *
     * <p>That is the point of separating the two resolutions: the fill wants a fine grid for a clean
     * silhouette and smooth lighting, the wireframe wants a coarse one to stay legible.
     */
    @Test
    void aFineGridIsDrawnAtRoughlyTheSameDensityAsACoarseOne() {
        for (int steps : new int[] {40, 60, 120, 400}) {
            double lines = (steps - 1) / (double) SurfaceCanvas.wireframeStride(steps);
            assertTrue(lines >= 14 && lines <= 28, steps + " samples would draw " + lines + " lines");
        }
    }
}
