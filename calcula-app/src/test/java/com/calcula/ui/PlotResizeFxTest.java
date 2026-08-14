package com.calcula.ui;

import java.io.File;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import com.calcula.expr.Expr;
import com.calcula.parse.Parser;
import com.calcula.plot.PlotValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A picture on the stack can be made a different size.
 *
 * <p>Height only. The width already grows with the column and is capped there; what was pinned was
 * the height, at 200 for a curve and 300 for a surface, however much room the window had.
 */
@Tag("fx")
class PlotResizeFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    /**
     * A real plot value, not the call somebody types.
     *
     * <p>{@code Parser.parse("Plot(sin(x), …)")} is a {@code Call("Plot", …)}; the value that lands on
     * the stack is {@code $Plot}. Built from the parsed call, the canvas draws "not a plot" — which
     * every assertion here passed straight over, and the rendered picture said in words.
     */
    private static Expr plot() {
        return PlotValue.of(Parser.parse("sin(x)"), "x", 0, 6);
    }

    /** The wrapper puts a grip under the picture, and the picture keeps its default height. */
    @Test
    void aPictureComesWithAGrip() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Expr value = plot();
        Region wrapped = FxTestSupport.callOnFx(() -> window.resizableForTest(value, 200));
        FxTestSupport.realizeThemed(new Pane(wrapped), Themes.PLATE);
        assertTrue(
                FxTestSupport.callOnFx(() -> wrapped.lookup(".plot-grip") != null),
                "the grip is what makes it resizable by hand");
    }

    /**
     * The height belongs to the value, not to the row.
     *
     * <p>The stack is a virtualised list: a row is recycled as it scrolls, so a height kept on the
     * cell would be inherited by whatever value scrolled into it next — a plot would silently take the
     * size of a different plot. Keyed by the value, a rebuilt cell comes back the size it was left.
     */
    @Test
    void aResizedPictureIsStillThatSizeWhenItsRowIsRebuilt() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Expr value = plot();
        FxTestSupport.runOnFx(() -> window.setPlotHeightForTest(value, 420));

        Region rebuilt = FxTestSupport.callOnFx(() -> window.resizableForTest(value, 200));
        double height = FxTestSupport.callOnFx(
                () -> ((Region) rebuilt.getChildrenUnmodifiable().get(0)).getPrefHeight());
        assertEquals(420, height, 0.5);
    }

    /** Clamped: a picture dragged to nothing could not be dragged back, having no grip left to grab. */
    @Test
    void aHeightIsClamped() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Expr value = plot();
        FxTestSupport.runOnFx(() -> window.setPlotHeightForTest(value, -500));
        assertTrue(FxTestSupport.callOnFx(() -> window.plotHeightForTest(value, 200)) >= 120);
        FxTestSupport.runOnFx(() -> window.setPlotHeightForTest(value, 100_000));
        assertTrue(FxTestSupport.callOnFx(() -> window.plotHeightForTest(value, 200)) <= 900);
    }

    /**
     * And there is a curve in it.
     *
     * <p>Asserted as well as written out, because the first version of this test built the wrong kind
     * of value and every other assertion here passed: the grip was there, the height was stored, and
     * the canvas said "not a plot". Ink is the thing none of the others can see.
     */
    @Test
    void itRenders() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Expr value = plot();
        FxTestSupport.runOnFx(() -> window.setPlotHeightForTest(value, 380));
        Region wrapped = FxTestSupport.callOnFx(() -> window.resizableForTest(value, 200));
        FxTestSupport.realizeThemed(new Pane(wrapped), Themes.PLATE);
        FxTestSupport.runOnFx(() -> {
            wrapped.autosize();
            wrapped.layout();
        });
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.web("#fbfbfd"));
        WritableImage image = FxTestSupport.callOnFx(() -> wrapped.snapshot(params, null));
        int drawn = 0;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (image.getPixelReader().getColor(x, y).getBlue() > 0.55
                        && image.getPixelReader().getColor(x, y).getRed() < 0.45) {
                    drawn++;
                }
            }
        }
        assertTrue(drawn > 200, "there should be a curve in it, not a message: " + drawn + " curve pixels");

        File out = new File("target/plot-resized.png");
        javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out);
        System.out.println("WROTE " + out.getAbsolutePath() + "  " + image.getWidth() + "x" + image.getHeight());
    }
}
