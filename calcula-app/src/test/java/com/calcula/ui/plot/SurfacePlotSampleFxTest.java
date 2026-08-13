package com.calcula.ui.plot;

import java.io.File;
import java.util.function.DoubleBinaryOperator;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

import com.calcula.plot.SurfaceSampler;
import com.calcula.ui.FxTestSupport;
import com.calcula.ui.Themes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Draws two surfaces per theme and leaves the PNG in {@code target/}, the way {@link PlotSampleFxTest}
 * does for curves.
 *
 * <p>Worth the file it writes. The pure tests can prove a cell is lit correctly and the pixel tests
 * can prove the silhouette is filled; neither can see that the height scale, drawn at the corner
 * furthest from the viewer, lands near the middle of the picture and prints its numbers in grey over
 * a blue mesh. That was legible on a low mound and gone on a tall one — visible in one glance,
 * invisible to every assertion pointed at it.
 */
@Tag("fx")
class SurfacePlotSampleFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @ParameterizedTest
    @EnumSource(Themes.class)
    void surfacesLookLikeSurfaces(Themes theme) throws Exception {
        SurfaceCanvas peak = canvas((x, y) -> Math.exp(-(x * x + y * y) / 3), -3, 3, 40);
        SurfaceCanvas ripple = canvas((x, y) -> Math.sin(x) * Math.cos(y), -6, 6, 60);

        HBox row = FxTestSupport.callOnFx(() -> {
            HBox box = new HBox(16, peak, ripple);
            box.setPadding(new Insets(16));
            return box;
        });
        FxTestSupport.realizeThemed(row, theme);

        WritableImage image = FxTestSupport.callOnFx(() -> {
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            return row.snapshot(params, null);
        });

        int drawn = 0;
        PixelReader pixels = image.getPixelReader();
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        Color ground = pixels.getColor(1, 1);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Color at = pixels.getColor(x, y);
                if (Math.abs(at.getRed() - ground.getRed()) > 0.02
                        || Math.abs(at.getGreen() - ground.getGreen()) > 0.02
                        || Math.abs(at.getBlue() - ground.getBlue()) > 0.02) {
                    drawn++;
                }
            }
        }
        // A canvas that draws nothing passes every structural assertion there is.
        assertTrue(drawn > 20_000, theme + ": the surfaces are essentially blank: " + drawn + " pixels");

        File out = new File("target/surface-sample-" + theme.name().toLowerCase() + ".png");
        javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out);
        System.out.println("WROTE " + out.getAbsolutePath() + "  " + drawn + " drawn pixels");
    }

    private static SurfaceCanvas canvas(DoubleBinaryOperator f, double lo, double hi, int steps) throws Exception {
        return FxTestSupport.callOnFx(() -> {
            SurfaceCanvas c = new SurfaceCanvas(420, 320);
            c.show(SurfaceSampler.sample(f, lo, hi, lo, hi, steps));
            return c;
        });
    }
}
