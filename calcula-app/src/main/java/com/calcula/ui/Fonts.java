package com.calcula.ui;

import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.scene.text.Font;

/**
 * Registers the bundled faces so a stylesheet can name them.
 *
 * <p>Bundled rather than requested, because naming a font you have not shipped is a preference, not a
 * decision: {@code -fx-font-family: "Inter", …} silently falls through to whatever the machine happens
 * to have, so the interface looked one way here and another way everywhere else — which is invisible
 * on the machine it was designed on.
 *
 * <p><b>Must run before any stylesheet is applied.</b> A family that is not registered when the sheet
 * naming it is parsed does not resolve, and JavaFX substitutes silently rather than complaining. That
 * ordering is why {@link Themes#apply} calls this rather than leaving it to a caller to remember.
 *
 * <p>Best effort per file: a font that fails to load costs its own face, not the window.
 */
public final class Fonts {

    private static final Logger LOG = Logger.getLogger(Fonts.class.getName());

    /** The interface face. Every scene's root is set to this by {@code ui-font.css}. */
    public static final String UI = "Inter";

    /** Chords, signatures, and anything that has to line up in a column. */
    public static final String MONO = "JetBrains Mono";

    /**
     * The face formulas are set in.
     *
     * <p>STIX Two Text, the family used for mathematics in scientific publishing. Bundled because the
     * alternative was JavaFX's logical {@code Serif}, which resolves to whatever serif the platform
     * happens to have — and for an application whose output IS the typeset mathematics, letting that
     * differ per platform is letting the product differ per platform. macOS ships STIX, which is
     * exactly why the gap was invisible here.
     */
    public static final String MATH = "STIX Two Text";

    private static final List<String> FILES = List.of(
            "inter/Inter-Regular.ttf",
            "inter/Inter-Italic.ttf",
            "inter/Inter-Bold.ttf",
            "inter/Inter-BoldItalic.ttf",
            "jetbrains-mono/JetBrainsMono-Regular.ttf",
            "jetbrains-mono/JetBrainsMono-Italic.ttf",
            "jetbrains-mono/JetBrainsMono-Bold.ttf",
            "jetbrains-mono/JetBrainsMono-BoldItalic.ttf",
            "stix-two-text/STIX2Text-Regular.otf",
            "stix-two-text/STIX2Text-Italic.otf",
            "stix-two-text/STIX2Text-Bold.otf",
            "stix-two-text/STIX2Text-BoldItalic.otf");

    /**
     * Volatile, so the fast path is a plain read rather than a lock.
     *
     * <p>{@code load()} is called once per formula rendered — the maths face has to be registered
     * whoever is doing the rendering, including the offscreen scene the clipboard picture uses, which
     * never applies a theme. Taking a monitor on every stack cell would be a real cost for a check
     * that is false exactly once.
     */
    private static volatile boolean loaded;

    private Fonts() {}

    /** Register every bundled face. Idempotent, and cheap enough to call from anywhere styling starts. */
    public static void load() {
        if (loaded) {
            return;
        }
        loadOnce();
    }

    private static synchronized void loadOnce() {
        if (loaded) {
            return; // another thread got here first
        }
        loaded = true;
        for (String file : FILES) {
            try (InputStream in = Fonts.class.getResourceAsStream("/com/calcula/fonts/" + file)) {
                if (in == null) {
                    LOG.warning("bundled font missing from the image: " + file);
                    continue;
                }
                // The size here is irrelevant — loadFont registers the family, and every use restates
                // the size it wants.
                if (Font.loadFont(in, 12) == null) {
                    LOG.warning("could not read bundled font: " + file);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "could not load bundled font " + file, e);
            }
        }
    }

    /** Visible for tests: whether a family is actually available to lay out with. */
    public static boolean isAvailable(String family) {
        return Font.getFamilies().contains(family);
    }
}
