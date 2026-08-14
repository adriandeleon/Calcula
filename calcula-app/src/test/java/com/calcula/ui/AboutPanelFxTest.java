package com.calcula.ui;

import java.io.File;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The About panel, rendered.
 *
 * <p>Leaves a PNG in {@code target/} under the same convention the plot samples use. What goes wrong
 * in a panel like this is alignment, wrapping, and a stylesheet that never arrived — and none of
 * those fails an assertion. The one thing worth asserting is the stylesheet, because a dialog has its
 * own scene and does not inherit the window's sheet: get that wrong and every rule here is silently
 * inert while the structure stays perfect.
 */
@Tag("fx")
class AboutPanelFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @ParameterizedTest
    @EnumSource(Themes.class)
    void theAboutPanelRenders(Themes theme) throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        VBox content = FxTestSupport.callOnFx(window::aboutContent);
        Pane holder = FxTestSupport.callOnFx(() -> new Pane(content));
        FxTestSupport.realizeThemed(holder, theme);
        FxTestSupport.runOnFx(() -> {
            content.applyCss();
            content.autosize();
            content.layout();
        });

        double nameSize = FxTestSupport.callOnFx(
                () -> content.lookup(".about-name").getLayoutBounds().getHeight());
        // The name is set at 26px by the sheet the dialog has to attach for itself. At the default
        // control size it would be around 16 — so this is really asking whether app.css arrived.
        assertTrue(nameSize > 22, theme + ": the about stylesheet did not reach the panel: " + nameSize);

        // The ground has to be the theme's. The panel carries no background of its own — in the real
        // dialog the DialogPane provides it — and the default snapshot fill is white, which draws the
        // dark theme's pale text onto white and produces a picture nobody will ever see.
        WritableImage image = FxTestSupport.callOnFx(() -> {
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(theme.dark() ? Color.web("#12151a") : Color.web("#fbfbfd"));
            return content.snapshot(params, null);
        });
        File out = new File("target/about-" + theme.name().toLowerCase() + ".png");
        javax.imageio.ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out);
        System.out.println("WROTE " + out.getAbsolutePath());
    }
}
