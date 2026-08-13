package com.calcula;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import com.calcula.cas.CasEngineLoader;
import com.calcula.config.Settings;
import com.calcula.ui.CalcWindow;
import com.calcula.ui.Themes;
import com.calcula.ui.WindowBounds;

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

    /**
     * Put the window back where it was, if that is still somewhere reachable.
     *
     * <p>The size is always restored; the POSITION only when enough of the window would land on a
     * screen that currently exists. A position saved on a monitor since unplugged, or on a laptop
     * since undocked, otherwise opens the calculator somewhere it cannot be dragged back from — and
     * the failure is total, because there is nothing on screen to grab.
     *
     * <p>Maximised is applied last and separately, so un-maximising returns to the remembered size
     * rather than to whatever the maximised bounds happened to be.
     */
    private void restoreBounds(Stage stage) {
        Settings saved = window.settings();
        stage.setWidth(saved.windowWidth());
        stage.setHeight(saved.windowHeight());
        List<Rectangle2D> screens =
                Screen.getScreens().stream().map(Screen::getVisualBounds).toList();
        if (WindowBounds.usable(saved.windowX(), saved.windowY(), saved.windowWidth(), saved.windowHeight(), screens)) {
            stage.setX(saved.windowX());
            stage.setY(saved.windowY());
        } else {
            stage.centerOnScreen();
        }
        stage.setMaximized(saved.windowMaximized());
    }

    /**
     * Follow the window around.
     *
     * <p>While maximised the stage reports the maximised geometry, which is the screen rather than a
     * choice the user made — writing it down would mean un-maximising to a full-screen "window"
     * forever after. So only the un-maximised geometry is recorded, and the flag is recorded beside
     * it.
     */
    private void watchBounds(Stage stage) {
        Runnable remember = () -> {
            if (stage.isMaximized()) {
                Settings s = window.settings();
                window.rememberWindow(s.windowX(), s.windowY(), s.windowWidth(), s.windowHeight(), true);
            } else {
                window.rememberWindow(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(), false);
            }
        };
        stage.xProperty().addListener((o, a, b) -> remember.run());
        stage.yProperty().addListener((o, a, b) -> remember.run());
        stage.widthProperty().addListener((o, a, b) -> remember.run());
        stage.heightProperty().addListener((o, a, b) -> remember.run());
        stage.maximizedProperty().addListener((o, a, b) -> remember.run());
    }

    @Override
    public void start(Stage stage) {
        window = new CalcWindow();
        Scene scene = new Scene(
                window.getRoot(),
                window.settings().windowWidth(),
                window.settings().windowHeight());

        // Owns the load order: Primer (user agent) -> theme tokens -> app.css. app.css is written
        // entirely in -color-*/-calc-* tokens, so applying it before the sheet that defines them
        // leaves every colour unresolved and the window renders in JavaFX's defaults.
        // The saved theme, and thereafter whatever Settings changes it to. CalcWindow owns the
        // preference; the Scene is App's, so applying it has to come back here.
        Themes.apply(scene, Themes.byName(window.settings().themeId()));
        window.setOnThemeChanged(theme -> Themes.apply(scene, theme));

        stage.setTitle(window.title());
        // The title carries the sheet's name and whether it is saved, so it has to follow both.
        window.setOnSheetChanged(() -> stage.setTitle(window.title()));
        // The close button is an exit like any other, and the one people use. Consuming the request
        // is what lets the prompt say no; without this the window shuts while the dialog is still up.
        stage.setOnCloseRequest(event -> {
            if (!window.confirmClose()) {
                event.consume();
                return;
            }
            // The debounce is still holding the last move; closing is not a reason to lose it.
            window.flushSettings();
        });
        stage.setScene(scene);
        stage.getIcons().addAll(icons());
        restoreBounds(stage);
        stage.show();
        watchBounds(stage);
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
