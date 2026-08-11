package com.calcula.ui.plot;

import java.io.File;
import java.util.List;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import com.calcula.parse.Parser;
import com.calcula.plot.ExprCompiler;
import com.calcula.ui.FxTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Draws a few curves, asserts there is ink on the canvas, and leaves the PNG in {@code target/}.
 *
 * <p>The pure tests can prove {@code 1/x} yields two segments; only a picture shows whether the result
 * looks like a plot. The math layout had four bugs that fifteen green structural tests missed and a
 * single glance caught, so the same check applies here.
 */
@Tag("fx")
class PlotSampleFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @Test
    void curvesActuallyDraw() throws Exception {
        List<String> formulas = List.of("sin(x)", "1/x", "x^3 - 3*x", "sin(x)/x", "tan(x)");
        VBox column = FxTestSupport.callOnFx(() -> {
            VBox box = new VBox(10);
            box.setPadding(new Insets(12));
            box.setStyle("-fx-background-color: #12151a;");
            for (String f : formulas) {
                Text caption = new Text(f);
                caption.setFill(Color.web("#8a8f98"));
                PlotCanvas canvas = new PlotCanvas(420, 150);
                canvas.show(ExprCompiler.compile(Parser.parse(f), "x"), -8, 8);
                box.getChildren().addAll(caption, canvas);
            }
            return box;
        });
        FxTestSupport.runOnFx(() -> {
            new Scene(column);
            column.applyCss();
            column.layout();
            SnapshotParameters p = new SnapshotParameters();
            p.setFill(Color.web("#12151a"));
            WritableImage image = column.snapshot(p, null);

            int lit = 0;
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    if (image.getPixelReader().getColor(x, y).getBrightness() > 0.25) {
                        lit++;
                    }
                }
            }
            // A canvas that draws nothing passes every structural assertion there is.
            assertTrue(lit > 2000, "the plots are essentially blank: " + lit + " lit pixels");
            try {
                File out = new File("target/plot-sample.png");
                javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out);
                System.out.println("WROTE " + out.getAbsolutePath() + "  " + lit + " lit pixels");
            } catch (Exception e) {
                throw new IllegalStateException("could not write the sample", e);
            }
        });
    }
}
