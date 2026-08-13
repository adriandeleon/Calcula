package com.calcula.ui.plot;

import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import com.calcula.plot.SurfaceProjection;
import com.calcula.plot.SurfaceSampler;
import com.calcula.ui.FxTestSupport;
import com.calcula.ui.Themes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    /**
     * Put the canvas on a scene <em>inside a holder</em>, themed, and hand it back.
     *
     * <p><b>Never as the scene's own root.</b> JavaFX gives a root the {@code root} style class, and
     * AtlantaFX paints {@code .root} with an opaque background — so a canvas made the root comes back
     * from a snapshot fully opaque no matter what was drawn on it, and any measurement of ink is
     * really a measurement of the theme. A root is also stretched to the scene, so a canvas asked for
     * at 320x240 quietly becomes 980x660.
     */
    private static SurfaceCanvas onScene(SurfaceCanvas canvas, Themes theme) throws Exception {
        Pane holder = FxTestSupport.callOnFx(() -> new Pane(canvas));
        FxTestSupport.realizeThemed(holder, theme);
        FxTestSupport.runOnFx(() -> {
            canvas.resize(canvas.getPrefWidth(), canvas.getPrefHeight());
            canvas.layout();
        });
        return canvas;
    }

    /**
     * What share of the canvas has something on it other than its own background.
     *
     * <p>Compared against the colour in the corner rather than against transparency. The plot has an
     * opaque inset background of its own, exactly as it does in the window, so "has anything been
     * drawn here" can only mean "does this differ from the ground" — counting non-transparent pixels
     * answers a question about the theme and returns 1.0 for a blank canvas.
     */
    private static double paintedShare(SurfaceCanvas canvas) throws Exception {
        WritableImage image = FxTestSupport.callOnFx(() -> {
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            return canvas.snapshot(params, null);
        });
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader pixels = image.getPixelReader();
        Color ground = pixels.getColor(1, 1);
        int painted = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (differs(pixels.getColor(x, y), ground)) {
                    painted++;
                }
            }
        }
        return painted / (double) (width * height);
    }

    private static boolean differs(Color a, Color b) {
        return Math.abs(a.getRed() - b.getRed()) > 0.02
                || Math.abs(a.getGreen() - b.getGreen()) > 0.02
                || Math.abs(a.getBlue() - b.getBlue()) > 0.02
                || Math.abs(a.getOpacity() - b.getOpacity()) > 0.02;
    }

    /** The share of the canvas covered by wireframe, as opposed to fill. */
    private static double strokeShare(SurfaceCanvas canvas) throws Exception {
        WritableImage image = FxTestSupport.callOnFx(() -> {
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            return canvas.snapshot(params, null);
        });
        Color curve = FxTestSupport.callOnFx(canvas::curveColour);
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader pixels = image.getPixelReader();
        int strokes = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Color at = pixels.getColor(x, y);
                if (Math.abs(at.getRed() - curve.getRed()) < 0.12
                        && Math.abs(at.getGreen() - curve.getGreen()) < 0.12
                        && Math.abs(at.getBlue() - curve.getBlue()) < 0.12) {
                    strokes++;
                }
            }
        }
        return strokes / (double) (width * height);
    }

    /**
     * Sampling a surface more finely must not draw more wireframe.
     *
     * <p>The two are separate resolutions: the fill wants a fine grid so the silhouette is clean and
     * the lighting smooth, the wireframe wants a coarse one so the picture reads as a shape. Tied
     * together, the default 60 samples put 60 lines each way on an oscillating function and they cross
     * into moire — a hairball that is technically a correct drawing of the surface.
     */
    @Test
    void aFinerSurfaceIsNotADenserWireframe() throws Exception {
        double coarse = strokeShare(onScene(bowlWith(20), Themes.PLATE));
        double fine = strokeShare(onScene(bowlWith(60), Themes.PLATE));
        assertTrue(fine < coarse * 1.6, "60 samples drew far more line than 20: " + fine + " vs " + coarse);
    }

    private static SurfaceCanvas bowlWith(int steps) throws Exception {
        return FxTestSupport.callOnFx(() -> {
            SurfaceCanvas c = new SurfaceCanvas(320, 240);
            c.show(SurfaceSampler.sample((x, y) -> x * x + y * y, -2, 2, -2, 2, steps));
            return c;
        });
    }

    @Test
    void aSurfaceReachesTheCanvas() throws Exception {
        SurfaceCanvas canvas = onScene(bowl(), Themes.PLATE);
        assertTrue(paintedShare(canvas) > 0.02, "a 20x20 mesh should put a good deal of ink down");
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
        onScene(whole, Themes.PLATE);

        SurfaceSampler.Grid holedGrid = SurfaceSampler.sample((x, y) -> 1 / (x * x + y * y), -2, 2, -2, 2, steps);
        assertFalse(holedGrid.finiteAt(steps / 2, steps / 2), "precondition: the grid really lands on the pole");

        SurfaceCanvas holed = FxTestSupport.callOnFx(() -> {
            SurfaceCanvas c = new SurfaceCanvas(320, 240);
            c.show(holedGrid);
            return c;
        });
        onScene(holed, Themes.PLATE);

        assertTrue(paintedShare(holed) > 0.02, "the rest of the surface is still drawn");
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
        onScene(empty, Themes.PLATE);
        assertTrue(paintedShare(empty) > 0, "an empty surface should still say something");
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
    // ------------------------------------------------------------- phase 4: solidity

    /**
     * The fill has to arrive from the theme, and it has to be opaque.
     *
     * <p>The one failure that would silently undo hidden-line removal. Cells are drawn far ones
     * first, so a near cell hides a far one purely by covering it; a fill that never resolved, or
     * resolved to something see-through, leaves the order perfectly correct and the surface perfectly
     * transparent — exactly the picture this replaced. Both themes, because a token can exist in one
     * palette and not the other.
     */
    @ParameterizedTest
    @EnumSource(Themes.class)
    void theSurfaceIsMadeOfTheGroundItSitsOn(Themes theme) throws Exception {
        SurfaceCanvas canvas = bowl();
        FxTestSupport.realizeThemed(canvas, theme);

        Color fill = FxTestSupport.callOnFx(canvas::surfaceFill);
        assertEquals(1.0, fill.getOpacity(), 1e-9, theme + ": a see-through fill hides nothing");
        assertNotEquals(Color.WHITE, fill, theme + ": still the built-in default, so the stylesheet never reached it");
    }

    /**
     * A surface is solid, not a wire cage.
     *
     * <p>Measured as the share of the canvas showing something other than its own ground. Removing
     * the {@code fillPolygon} takes this from 16% to 8% — an outline is a cage with holes between its
     * bars, and the difference is an object against a diagram of one.
     */
    @Test
    void aSurfaceIsSolidRatherThanSeeThrough() throws Exception {
        // Coarse on purpose: a fine mesh's own lines already cover much of the silhouette, so the
        // two cases differ by a few points and the threshold ends up sitting on noise. At eight steps
        // the outline is a thin cage and what is being measured is unambiguously the fill.
        SurfaceCanvas canvas = onScene(
                FxTestSupport.callOnFx(() -> {
                    SurfaceCanvas c = new SurfaceCanvas(320, 240);
                    c.show(SurfaceSampler.sample((x, y) -> x * x + y * y, -2, 2, -2, 2, 8));
                    return c;
                }),
                Themes.PLATE);

        double covered = paintedShare(canvas);
        assertTrue(covered > 0.12, "a filled surface covers its silhouette, not just its edges: " + covered);
    }

    /**
     * The height axis stands at the leftmost corner, and which corner that is follows the turn.
     *
     * <p>A fixed corner would be right until the first drag and then stand somewhere inside the
     * silhouette, printing the numbers over the object they are measuring.
     */
    @Test
    void theHeightAxisFollowsTheTurn() throws Exception {
        SurfaceCanvas canvas = bowl();
        FxTestSupport.realizeThemed(canvas, Themes.PLATE);

        double[] before = FxTestSupport.callOnFx(canvas::heightAxisCorner);
        // Half a turn: the corner that was at the back is now at the front.
        FxTestSupport.runOnFx(() -> canvas.drag(Math.PI / (Math.PI / 260) / 2, 0));
        double[] after = FxTestSupport.callOnFx(canvas::heightAxisCorner);

        assertNotEquals(
                before[0] + "," + before[1],
                after[0] + "," + after[1],
                "turned right round, the far corner cannot still be the same one");
    }
}
