package com.calcula.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Region;

import org.testfx.api.FxToolkit;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Shared plumbing for the headless-JavaFX tests (tagged {@code fx}). Boots the toolkit once via
 * JavaFX 26's built-in Headless Glass platform — no Monocle jar, no display, no xvfb.
 *
 * <p>Mirrors whatever {@code App.start} sets up before it builds a window; if the two drift, the scene
 * under test stops being the scene the app really has.
 */
public final class FxTestSupport {

    private static volatile boolean booted;

    private FxTestSupport() {}

    /**
     * Point the config directory at a throwaway, before anything can read the real one.
     *
     * <p>{@code CalcWindow} loads settings when it is constructed and saves them when they change, so
     * without this the suite would take its behaviour from whatever preferences the developer happens
     * to have — a test asserting algebraic entry would fail on a machine set to RPN — and could write
     * over them. A test must not be able to change the machine it is running on.
     */
    private static void useScratchConfigDir() throws java.io.IOException {
        if (System.getProperty("calcula.config.dir") == null) {
            freshConfigDir();
        }
    }

    /**
     * Point the config directory at a brand-new throwaway.
     *
     * <p>Any test that SAVES settings must call this first. One scratch directory for the whole suite
     * is not enough: the tests share a JVM, so a test that saves a preference changes what every later
     * test loads — a mode saved by one made another see degrees where it expected radians, which is
     * order-dependent and miserable to diagnose once there are more tests than you can read at once.
     */
    public static void freshConfigDir() throws java.io.IOException {
        java.nio.file.Path scratch = java.nio.file.Files.createTempDirectory("calcula-test-config");
        scratch.toFile().deleteOnExit();
        System.setProperty("calcula.config.dir", scratch.toString());
    }

    public static synchronized void bootToolkit() throws Exception {
        if (booted) {
            return;
        }
        useScratchConfigDir();
        FxToolkit.registerPrimaryStage();
        runOnFx(() -> {
            // The AtlantaFX user-agent stylesheet defines the -color-* vars app.css resolves against.
            // Without it every lookup logs a harmless but very noisy CSS warning.
            javafx.application.Application.setUserAgentStylesheet(
                    new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet());
            Thread.currentThread().setContextClassLoader(FxTestSupport.class.getClassLoader());
        });
        booted = true;
    }

    /** Run {@code task} on the FX thread and block until it finishes, rethrowing any failure. */
    public static void runOnFx(Runnable task) throws Exception {
        if (Platform.isFxApplicationThread()) {
            task.run();
            return;
        }
        var error = new AtomicReference<Throwable>();
        var latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(20, SECONDS)) {
            throw new IllegalStateException("FX task timed out");
        }
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
    }

    /** Compute a value on the FX thread and return it (blocking). */
    public static <T> T callOnFx(Callable<T> task) throws Exception {
        var result = new AtomicReference<T>();
        runOnFx(() -> {
            try {
                result.set(task.call());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return result.get();
    }

    /**
     * Put {@code root} in a Scene and force a CSS + layout pass, then return it.
     *
     * <p>Not optional. A JavaFX control builds its children in its SKIN, and the skin is created by the
     * CSS pass — so on a freshly-constructed graph a {@code SplitPane} has no content, a {@code
     * ScrollPane} has no viewport, and {@link javafx.scene.Node#lookup} on anything inside them returns
     * null. The graph looks right in the debugger and is empty to a selector, which is a confusing
     * afternoon if you meet it without knowing.
     */
    public static Region realize(Region root) throws Exception {
        runOnFx(() -> {
            new Scene(root, 980, 660);
            root.applyCss();
            root.layout();
        });
        return root;
    }

    /**
     * Like {@link #realize}, but with the application's own stylesheets on the scene.
     *
     * <p>{@code realize} gives a bare Scene, so a test using it sees <b>only AtlantaFX Primer</b> — the
     * user-agent sheet — and neither {@code app.css} nor the theme tokens. That is fine for structure
     * and silently wrong for anything measured: sizes, spacing, fonts and every {@code -color-*} come
     * from the sheets that are not there. A row pinned to the wrong height by a Primer rule that
     * {@code app.css} exists to override looks perfectly correct to a test built the bare way, because
     * the override was never loaded — which is the drift this class's own header warns about.
     *
     * <p>Use this whenever an assertion depends on what a rule computes rather than on which style
     * class is attached. It matches what {@code App.start} does, which is the only configuration that
     * ships.
     */
    public static Region realizeThemed(Region root, Themes theme) throws Exception {
        runOnFx(() -> {
            Scene scene = new Scene(root, 980, 660);
            Themes.apply(scene, theme);
            root.applyCss();
            root.layout();
        });
        return root;
    }

    /** Pump the FX event queue until {@code condition} holds, or fail after {@code timeoutMs}. */
    public static void waitFor(String what, long timeoutMs, Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(callOnFx(condition))) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("timed out waiting for " + what);
    }
}
