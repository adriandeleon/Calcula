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
    void theStackBreathes() throws Exception {
        // A fraction beside a long integer is the case that shows it: the fraction is a tall node, so
        // a fixed few pixels of padding leaves its denominator all but touching the entry below.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Region root = window.getRoot();
        FxTestSupport.runOnFx(() -> {
            Scene scene = new Scene(root, 980, 520);
            Themes.apply(scene, Themes.byName(window.settings().themeId()));
            root.applyCss();
            root.layout();
            window.submit("1/3 + 1/6");
        });
        FxTestSupport.waitFor("the first", 5000, () -> !window.stackContents().isEmpty());
        FxTestSupport.runOnFx(() -> window.submit("2^128"));
        FxTestSupport.waitFor("the second", 5000, () -> window.stackContents().size() >= 2);
        FxTestSupport.runOnFx(() -> window.submit("N(pi, 40)"));
        FxTestSupport.waitFor("the third", 5000, () -> window.stackContents().size() >= 3);
        FxTestSupport.runOnFx(() -> {
            root.applyCss();
            root.layout();
        });
        WritableImage image = FxTestSupport.callOnFx(() -> root.snapshot(new SnapshotParameters(), null));
        javax.imageio.ImageIO.write(
                javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", new File("target/stack-sample.png"));
        System.out.println("WROTE target/stack-sample.png");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void everyGlyphRenders() throws Exception {
        // A sheet of all of them at size. IconsFxTest proves each PARSES; only a picture says whether
        // scissors read as scissors at sixteen units across.
        javafx.scene.layout.FlowPane sheet = new javafx.scene.layout.FlowPane(14, 14);
        sheet.setStyle("-fx-padding: 20; -fx-background-color: #14161c;");
        sheet.setPrefWrapLength(560);
        for (String name : Icons.names()) {
            javafx.scene.Node glyph = Icons.of(name);
            javafx.scene.control.Label label = new javafx.scene.control.Label(name);
            label.setStyle("-fx-font-family: Inter; -fx-font-size: 9px; -fx-text-fill: #8b93a7;");
            javafx.scene.layout.VBox cell = new javafx.scene.layout.VBox(6, glyph, label);
            cell.setAlignment(javafx.geometry.Pos.CENTER);
            cell.setPrefWidth(78);
            sheet.getChildren().add(cell);
        }
        FxTestSupport.runOnFx(() -> {
            Scene scene = new Scene(sheet);
            scene.getStylesheets()
                    .add(CalcWindow.class
                            .getResource("/com/calcula/styles/app.css")
                            .toExternalForm());
            sheet.applyCss();
            sheet.layout();
        });
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setTransform(new javafx.scene.transform.Scale(3, 3));
        WritableImage image = FxTestSupport.callOnFx(() -> sheet.snapshot(parameters, null));
        javax.imageio.ImageIO.write(
                javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", new File("target/glyphs-sample.png"));
        System.out.println("WROTE target/glyphs-sample.png");
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
