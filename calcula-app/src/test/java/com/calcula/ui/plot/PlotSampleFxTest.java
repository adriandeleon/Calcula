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

import com.calcula.expr.Expr;
import com.calcula.parse.Parser;
import com.calcula.plot.ExprCompiler;
import com.calcula.plot.GraphicsScene;
import com.calcula.plot.PlotAnalysis;
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

    private static Text caption(String text) {
        Text t = new Text(text);
        t.setFill(Color.web("#8a8f98"));
        return t;
    }

    private static PlotCanvas plain(String source) {
        PlotCanvas canvas = new PlotCanvas(420, 150);
        canvas.show(ExprCompiler.compile(Parser.parse(source), "x"), -8, 8);
        return canvas;
    }

    private static PlotCanvas annotated(String source, List<Expr> poles, List<Expr> turning) {
        Expr body = Parser.parse(source);
        PlotCanvas canvas = new PlotCanvas(420, 150);
        canvas.show(ExprCompiler.compile(body, "x"), -8, 8);
        canvas.setAnalysis(PlotAnalysis.of(poles, turning, ExprCompiler.compile(body, "x"), -8, 8));
        return canvas;
    }

    private static PlotCanvas scene() {
        StringBuilder points = new StringBuilder("[");
        for (int i = 0; i <= 60; i++) {
            double x = i / 60.0 * 6;
            points.append(i == 0 ? "" : ", ")
                    .append("[")
                    .append(x)
                    .append(", ")
                    .append(Math.sin(x))
                    .append("]");
        }
        points.append("]");
        PlotCanvas canvas = new PlotCanvas(420, 150);
        canvas.showScene(
                GraphicsScene.parse(Parser.parse("Graphics([RGBColor(0.06, 0.58, 0.53), Line(" + points + ")])")));
        return canvas;
    }

    @Test
    void curvesActuallyDraw() throws Exception {
        List<String> formulas = List.of("sin(x)", "1/x", "x^3 - 3*x", "sin(x)/x", "tan(x)");
        VBox column = FxTestSupport.callOnFx(() -> {
            VBox box = new VBox(10);
            box.setPadding(new Insets(12));
            box.setStyle("-fx-background-color: #12151a;");
            for (String f : formulas) {
                box.getChildren().addAll(caption(f), plain(f));
            }
            // Annotated: the roots here are what the engine returns for these, verified against it.
            box.getChildren()
                    .addAll(
                            caption("1/(x^2-1)  — poles marked"),
                            annotated(
                                    "1/(x^2-1)",
                                    List.of(Parser.parse("-1"), Parser.parse("1")),
                                    List.of(Parser.parse("0"))));
            box.getChildren()
                    .addAll(
                            caption("x^3 - 3*x  — turning points, exact"),
                            annotated("x^3 - 3*x", List.of(), List.of(Parser.parse("-1"), Parser.parse("1"))));
            box.getChildren()
                    .addAll(
                            caption("sin(x)  — labelled with the exact root"),
                            annotated("sin(x)", List.of(), List.of(Parser.parse("pi/2"))));
            // A picture the engine produced, in the shape its Plot returns.
            box.getChildren().addAll(caption("engine Graphics, adaptively sampled"), scene());
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
