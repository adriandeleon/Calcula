package com.calcula;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import com.calcula.cas.CasEngineLoader;
import com.calcula.ui.CalcWindow;
import com.calcula.ui.Themes;

/** Entry point. */
public final class App extends Application {

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    private CalcWindow window;

    public static void main(String[] args) {
        // MUST be first, before any AWT class can load. Anything that touches Java2D later — rasterising
        // a plot for export, JLaTeXMath rendering — makes the macOS AWT native pipeline contend with
        // JavaFX's Glass for the single AppKit run loop, and the app intermittently deadlocks. Headless
        // Java2D rasterises in software and the conflict disappears.
        System.setProperty("java.awt.headless", "true");

        SessionLog.install();
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        window = new CalcWindow();
        Scene scene = new Scene(window.getRoot(), 980, 660);

        // Owns the load order: Primer (user agent) -> theme tokens -> app.css. app.css is written
        // entirely in -color-*/-calc-* tokens, so applying it before the sheet that defines them
        // leaves every colour unresolved and the window renders in JavaFX's defaults.
        Themes.apply(scene, Themes.DEFAULT);

        stage.setTitle(window.title());
        stage.setScene(scene);
        stage.getIcons().addAll(icons());
        stage.show();
        window.focusInput();

        // Off the FX thread on purpose: Symja's static init is ~650 ms, which would be 650 ms of blank
        // window. The UI is fully usable meanwhile; the mode line says so.
        CasEngineLoader.loadAsync(CasEngineLoader.defaultCasDir())
                .thenAccept(engine -> window.setEngine(engine))
                .exceptionally(t -> {
                    LOG.log(Level.WARNING, "CAS load failed", t);
                    return null;
                });
    }

    /**
     * The window icons, largest first.
     *
     * <p>Rasters rather than the SVG master: the master sets the integral as text and so depends on
     * a math font being installed, which is precisely the failure this avoids. The 16 and 24 px
     * entries are the reduced mark — the integral alone — because below 32 px the stack rules turn
     * into a smudge.
     *
     * <p>Best-effort. A missing icon is a packaging fault and leaves the OS default in place; it is
     * not worth refusing to open a window over.
     */
    private static List<Image> icons() {
        List<Image> loaded = new ArrayList<>();
        for (int size : new int[] {512, 256, 128, 64, 48, 32, 24, 16}) {
            try (InputStream in = App.class.getResourceAsStream("icons/calcula-" + size + ".png")) {
                if (in != null) {
                    loaded.add(new Image(in));
                }
            } catch (Exception e) {
                LOG.log(Level.FINE, "icon " + size + " unavailable", e);
            }
        }
        return loaded;
    }

    @Override
    public void stop() {
        if (window != null) {
            window.dispose();
        }
    }
}
