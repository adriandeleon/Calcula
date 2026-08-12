package com.calcula.ui;

import java.io.File;
import java.nio.file.Path;

import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes pictures of the new surfaces to {@code target/} so they can be looked at.
 *
 * <p>Not decoration. Four separate layout faults on this project were found by rendering something and
 * looking at it while a full suite of structural tests stayed green — a test can assert that a node
 * exists, has the right text and is in the right parent, and still not notice that it is drawn on top
 * of another one. The ink assertion here is a floor, not the point; the point is the file.
 *
 * <p>{@code mvn test -Dtest=SurfaceSampleFxTest}
 */
@Tag("fx")
class SurfaceSampleFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @org.junit.jupiter.api.BeforeEach
    void isolate() throws Exception {
        FxTestSupport.freshConfigDir();
    }

    @Test
    void theEmptyWindowRenders() throws Exception {
        snapshot("empty-sample.png", null);
    }

    @Test
    void aSelectedPartRenders() throws Exception {
        // Look at it: the selection has to be visible enough to know what a command will act on, and
        // subtle enough not to fight the formula it is inside.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Region root = window.getRoot();
        FxTestSupport.runOnFx(() -> {
            Scene scene = new Scene(root, 980, 660);
            Themes.apply(scene, Themes.byName(window.settings().themeId()));
            root.applyCss();
            root.layout();
            window.submit("integrate(x*sin(x), x)");
        });
        FxTestSupport.waitFor("the entry", 5000, () -> !window.stackContents().isEmpty());
        FxTestSupport.runOnFx(
                () -> window.selectPart(1, com.calcula.parse.Parser.parse("sin(x)"), java.util.List.of(0, 1)));
        FxTestSupport.runOnFx(() -> {
            root.applyCss();
            root.layout();
        });
        WritableImage image = FxTestSupport.callOnFx(() -> root.snapshot(new SnapshotParameters(), null));
        javax.imageio.ImageIO.write(
                javafx.embed.swing.SwingFXUtils.fromFXImage(image, null),
                "png",
                new File("target/selection-sample.png"));
        System.out.println("WROTE target/selection-sample.png");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theTrailAndItsZoomButtonsRender() throws Exception {
        // The trail bar is two 16-unit glyphs in a pane that is a quarter of the window. Whether they
        // are legible at that size is not something a structural test can answer.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Region root = window.getRoot();
        FxTestSupport.runOnFx(() -> {
            Scene scene = new Scene(root, 980, 660);
            Themes.apply(scene, Themes.byName(window.settings().themeId()));
            root.applyCss();
            root.layout();
            window.submit("integrate(x*sin(x), x)");
        });
        FxTestSupport.waitFor("the entry", 5000, () -> !window.stackContents().isEmpty());
        FxTestSupport.runOnFx(() -> {
            window.submit("1/3 + 1/6");
            window.run("trail.zoomIn");
            window.run("trail.zoomIn");
            root.applyCss();
            root.layout();
        });
        WritableImage image = FxTestSupport.callOnFx(() -> root.snapshot(new SnapshotParameters(), null));
        javax.imageio.ImageIO.write(
                javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", new File("target/trail-sample.png"));
        System.out.println("WROTE target/trail-sample.png");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theFunctionSheetRenders() throws Exception {
        snapshot("functions-sample.png", "help.functions");
    }

    @Test
    void theTabStripRenders() throws Exception {
        // Three sheets, one of them unsaved: whether a tab reads as selected, as modified, or as
        // neither is a question only a picture answers.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Region root = window.getRoot();
        FxTestSupport.runOnFx(() -> {
            Scene scene = new Scene(root, 980, 660);
            Themes.apply(scene, Themes.byName(window.settings().themeId()));
            root.applyCss();
            root.layout();
            window.submit("integrate(x*sin(x), x)");
        });
        FxTestSupport.waitFor("the entry", 5000, () -> !window.stackContents().isEmpty());
        FxTestSupport.runOnFx(() -> {
            window.newSheet();
            window.newSheet();
            window.submit("1/3 + 1/6");
        });
        FxTestSupport.waitFor(
                "the third sheet", 5000, () -> !window.stackContents().isEmpty());
        FxTestSupport.runOnFx(() -> {
            root.applyCss();
            root.layout();
        });
        WritableImage image = FxTestSupport.callOnFx(() -> root.snapshot(new SnapshotParameters(), null));
        javax.imageio.ImageIO.write(
                javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", new File("target/tabs-sample.png"));
        System.out.println("WROTE target/tabs-sample.png");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theExampleSheetRenders() throws Exception {
        snapshot("examples-sample.png", "help.examples");
    }

    @Test
    void thePaletteRenders() throws Exception {
        snapshot("palette-sample.png", "app.palette");
    }

    @Test
    void theSettingsCardRenders() throws Exception {
        snapshot("settings-sample.png", "app.settings");
    }

    private void snapshot(String name, String command) throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Region root = window.getRoot();
        FxTestSupport.runOnFx(() -> {
            Scene scene = new Scene(root, 980, 660);
            // Mirrors App.start. Without it app.css is absent and the picture shows an UNSTYLED
            // palette — which looks exactly like a CSS bug and sent me looking for one.
            Themes.apply(scene, Themes.byName(window.settings().themeId()));
            root.applyCss();
            root.layout();
            // Something on the stack, so the picture shows the overlay against real content rather
            // than against an empty window.
            if (command != null) {
                window.submit("integrate(x*sin(x), x)");
            }
        });
        if (command != null) {
            FxTestSupport.runOnFx(() -> window.run(command));
        }
        FxTestSupport.runOnFx(() -> {
            root.applyCss();
            root.layout();
        });

        WritableImage image = FxTestSupport.callOnFx(() -> root.snapshot(new SnapshotParameters(), null));
        Path out = Path.of("target", name);
        javax.imageio.ImageIO.write(
                javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", new File(out.toString()));

        int lit = 0;
        for (int x = 0; x < image.getWidth(); x += 2) {
            for (int y = 0; y < image.getHeight(); y += 2) {
                if (image.getPixelReader().getColor(x, y).getBrightness() > 0.55) {
                    lit++;
                }
            }
        }
        System.out.println("WROTE " + out.toAbsolutePath() + "  " + lit + " lit pixels");
        assertTrue(lit > 150, "the picture looks blank: " + lit + " lit pixels");
        FxTestSupport.runOnFx(window::dispose);
    }
}
