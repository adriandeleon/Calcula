package com.calcula;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.util.Duration;

import com.calcula.cas.CasEngineLoader;
import com.calcula.ui.CalcWindow;
import com.calcula.ui.Themes;

/** Entry point. */
public final class App extends Application {

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    /** How long the AOT training run keeps the window up before quitting. */
    private static final double TRAIN_SETTLE_SECONDS = 2.5;

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

        maybeExitAfterTraining();
    }

    /**
     * The AOT training run: render, settle, quit.
     *
     * <p>The build trains the cache by launching this application for real and letting it exit itself.
     * It has to be a real window rather than a headless start, because the whole win is JavaFX's
     * scene/control/CSS class loading, and none of that happens until something renders — a headless
     * trainer measures as gaining approximately nothing.
     *
     * <p>The settle is what makes the cache worth having: the first frame is only the beginning, and
     * the debounced highlighting, the typeset layout and the CAS load all come after it. Exiting on the
     * first pulse would archive a fraction of what a real session touches.
     *
     * <p>Inert unless the property is set, so it costs a delivered app one system-property read.
     */
    private static void maybeExitAfterTraining() {
        if (System.getProperty("calcula.aotTrainExit") == null) {
            return;
        }
        LOG.info("AOT training run: exiting once the window has settled");
        PauseTransition settle = new PauseTransition(Duration.seconds(TRAIN_SETTLE_SECONDS));
        // Halt rather than Platform.exit: a clean shutdown would tear down the CAS loader mid-flight,
        // and the point here is the class list already recorded, not an orderly close.
        settle.setOnFinished(e -> Runtime.getRuntime().halt(0));
        settle.play();
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
