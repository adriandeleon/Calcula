package com.calcula.ui.plot;

import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;

import com.calcula.plot.SurfaceProjection;
import com.calcula.plot.SurfaceSampler;
import com.calcula.ui.FxTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The surface, drawn.
 *
 * <p>{@link com.calcula.plot.SurfaceProjectionTest} checks where the points go; this checks that
 * something reaches the canvas, that turning it changes the picture, and that a hole stays a hole.
 * Counting lit pixels rather than comparing against a reference image, because a reference image
 * fails on every font and pipeline it was not made on and teaches nobody anything when it does.
 */
@Tag("fx")
class SurfaceCanvasFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    private static SurfaceCanvas bowl() throws Exception {
        return FxTestSupport.callOnFx(() -> {
            SurfaceCanvas canvas = new SurfaceCanvas(320, 240);
            canvas.show(SurfaceSampler.sample((x, y) -> x * x + y * y, -2, 2, -2, 2, 20));
            return canvas;
        });
    }

    /** How much of the canvas has anything drawn on it. */
    private static int inked(SurfaceCanvas canvas) throws Exception {
        WritableImage image = FxTestSupport.callOnFx(() -> {
            Region wrapper = canvas;
            wrapper.applyCss();
            wrapper.layout();
            return wrapper.snapshot(null, null);
        });
        int count = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (image.getPixelReader().getColor(x, y).getOpacity() > 0.1) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void aSurfaceReachesTheCanvas() throws Exception {
        SurfaceCanvas canvas = bowl();
        FxTestSupport.realize(canvas);
        assertTrue(inked(canvas) > 500, "a 20x20 mesh should put a good deal of ink down");
    }

    @Test
    void turningItChangesThePicture() throws Exception {
        SurfaceCanvas canvas = bowl();
        FxTestSupport.realize(canvas);

        double before = FxTestSupport.callOnFx(() -> canvas.view().azimuth());
        FxTestSupport.runOnFx(() -> canvas.drag(60, 0));
        double after = FxTestSupport.callOnFx(() -> canvas.view().azimuth());

        assertTrue(Math.abs(after - before) > 0.1, "a drag across the canvas should turn it");
    }

    /** Elevation has ends, and dragging past them must settle rather than invert the picture. */
    @Test
    void turningPastTheEndsStops() throws Exception {
        SurfaceCanvas canvas = bowl();
        FxTestSupport.realize(canvas);

        FxTestSupport.runOnFx(() -> canvas.drag(0, 10_000));
        assertEquals(
                SurfaceProjection.MAX_ELEVATION,
                FxTestSupport.callOnFx(() -> canvas.view().elevation()),
                1e-9);

        FxTestSupport.runOnFx(() -> canvas.drag(0, -20_000));
        assertEquals(
                SurfaceProjection.MIN_ELEVATION,
                FxTestSupport.callOnFx(() -> canvas.view().elevation()),
                1e-9);
    }

    /**
     * A surface with a pole through it draws fewer cells than one without, because the four touching
     * the pole are not drawn at all. The hole is the honest picture.
     *
     * <p><b>The step count is odd on purpose.</b> Over [-2, 2] a grid only samples the origin when
     * there is a middle sample to land on it, so an even count steps straight over the singularity
     * and the "hole" case quietly becomes an ordinary surface with a very tall spike. Worth knowing
     * generally: a grid can miss a pole entirely, and missing it is not the same as there not being
     * one.
     */
    @Test
    void aHoleIsLeftWhereTheFunctionHasNoValue() throws Exception {
        int steps = 21;
        SurfaceCanvas whole = FxTestSupport.callOnFx(() -> {
            SurfaceCanvas c = new SurfaceCanvas(320, 240);
            c.show(SurfaceSampler.sample((x, y) -> x * x + y * y, -2, 2, -2, 2, steps));
            return c;
        });
        FxTestSupport.realize(whole);

        SurfaceSampler.Grid holedGrid = SurfaceSampler.sample((x, y) -> 1 / (x * x + y * y), -2, 2, -2, 2, steps);
        assertFalse(holedGrid.finiteAt(steps / 2, steps / 2), "precondition: the grid really lands on the pole");

        SurfaceCanvas holed = FxTestSupport.callOnFx(() -> {
            SurfaceCanvas c = new SurfaceCanvas(320, 240);
            c.show(holedGrid);
            return c;
        });
        FxTestSupport.realize(holed);

        assertTrue(inked(holed) > 0, "the rest of the surface is still drawn");
        assertTrue(countCells(holedGrid) < countCells(whole), "four cells touch the pole and none is drawn");
    }

    /** Cells with four finite corners — the ones that get drawn. */
    private static int countCells(SurfaceSampler.Grid g) {
        int n = 0;
        for (int i = 0; i < g.steps() - 1; i++) {
            for (int j = 0; j < g.steps() - 1; j++) {
                if (g.quadFinite(i, j)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int countCells(SurfaceCanvas canvas) throws Exception {
        return countCells(FxTestSupport.callOnFx(canvas::grid));
    }

    @Test
    void aSurfaceWithNothingInItSaysSoRatherThanDrawingNothing() throws Exception {
        SurfaceCanvas empty = FxTestSupport.callOnFx(() -> {
            SurfaceCanvas c = new SurfaceCanvas(320, 240);
            c.show(SurfaceSampler.sample((x, y) -> Double.NaN, -1, 1, -1, 1, 8));
            return c;
        });
        FxTestSupport.realize(empty);
        assertTrue(inked(empty) > 0, "an empty surface should still say something");
    }

    @Test
    void resizingKeepsTheAngleItWasTurnedTo() throws Exception {
        SurfaceCanvas canvas = bowl();
        FxTestSupport.realize(canvas);
        FxTestSupport.runOnFx(() -> canvas.drag(40, 20));
        double turned = FxTestSupport.callOnFx(() -> canvas.view().azimuth());

        FxTestSupport.runOnFx(() -> {
            canvas.resize(500, 400);
            canvas.layout();
        });

        assertEquals(turned, FxTestSupport.callOnFx(() -> canvas.view().azimuth()), 1e-9);
        assertEquals(500, FxTestSupport.callOnFx(() -> canvas.view().width()), 1e-9);
    }

    /** It takes its colours from the same rules a plot does, so a theme reaches it with no new CSS. */
    @Test
    void itIsStyledAsAPlot() throws Exception {
        SurfaceCanvas canvas = bowl();
        assertNotNull(canvas);
        assertTrue(canvas.getStyleClass().contains("plot-canvas"));
        assertTrue(canvas.getStyleClass().contains("surface-canvas"));
    }
}
