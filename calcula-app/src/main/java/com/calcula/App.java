package com.calcula;

import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import atlantafx.base.theme.PrimerDark;
import com.calcula.cas.CasEngineLoader;
import com.calcula.ui.CalcWindow;

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
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

        window = new CalcWindow();
        Scene scene = new Scene(window.getRoot(), 980, 660);
        scene.getStylesheets().add(App.class.getResource("styles/app.css").toExternalForm());

        stage.setTitle(window.title());
        stage.setScene(scene);
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

    @Override
    public void stop() {
        if (window != null) {
            window.dispose();
        }
    }
}
