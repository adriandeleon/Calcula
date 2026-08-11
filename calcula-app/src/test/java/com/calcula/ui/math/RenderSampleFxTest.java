package com.calcula.ui.math;

import java.io.File;
import java.util.List;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import com.calcula.parse.Parser;
import com.calcula.ui.FxTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Renders a sheet of formulas, asserts it actually has ink on it, and leaves the PNG in {@code target/}
 * to be looked at.
 *
 * <p>The looking matters. Every structural test in {@link MathLayoutFxTest} passed while nested rows
 * were dropping off the baseline and fraction bars were running the full width of the window — a
 * picture showed all of it in one glance. The pixel assertion here guards the case those tests cannot
 * see at all: a formula that renders as nothing.
 */
@Tag("fx")
class RenderSampleFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    /** The test harness runs a dark theme; force ink so the sheet is legible. */
    private static void paintBlack(javafx.scene.Node node) {
        if (node instanceof javafx.scene.text.Text t) {
            t.setFill(javafx.scene.paint.Color.BLACK);
        } else if (node instanceof javafx.scene.shape.Shape s) {
            s.setStroke(javafx.scene.paint.Color.BLACK);
            s.setFill(
                    node instanceof javafx.scene.shape.Rectangle
                            ? javafx.scene.paint.Color.BLACK
                            : javafx.scene.paint.Color.TRANSPARENT);
        }
        if (node instanceof javafx.scene.Parent p) {
            p.getChildrenUnmodifiable().forEach(RenderSampleFxTest::paintBlack);
        }
    }

    @Test
    void everyFormulaActuallyDrawsSomething() throws Exception {
        List<String> formulas = List.of(
                "1/2 + 1/3",
                "(x+1)/(x^2-3)",
                "sqrt(1-x^2)",
                "x^2 + 2*x + 1",
                "sin(x)/x",
                "[[1,2],[3,4]]",
                "solve(x^2 = 4, x)",
                "pi^2/6",
                "e^(i*pi) + 1 = 0",
                "(a+b)^2/(c-d)");
        VBox column = FxTestSupport.callOnFx(() -> {
            VBox box = new VBox(18);
            box.setPadding(new Insets(24));
            box.setStyle("-fx-background-color: white;");
            for (String f : formulas) {
                Region r = MathLayout.render(Parser.parse(f), MathStyle.of(22));
                paintBlack(r);
                box.getChildren().add(r);
            }
            return box;
        });
        FxTestSupport.runOnFx(() -> {
            new Scene(column);
            column.applyCss();
            column.layout();
            paintBlack(column);
            SnapshotParameters p = new SnapshotParameters();
            p.setFill(Color.WHITE);
            WritableImage image = column.snapshot(p, null);
            int ink = 0;
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    if (image.getPixelReader().getColor(x, y).getBrightness() < 0.5) {
                        ink++;
                    }
                }
            }
            // A blank or near-blank sheet means the renderer produced boxes with nothing in them,
            // which no structural assertion would notice.
            if (ink < 500) {
                throw new AssertionError("the rendered sheet is essentially blank: " + ink + " dark pixels");
            }
            try {
                File out = new File("target/math-sample.png");
                javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out);
                System.out.println("WROTE " + out.getAbsolutePath() + "  " + ink + " dark pixels");
            } catch (Exception e) {
                throw new IllegalStateException("could not write the sample", e);
            }
        });
    }
}
