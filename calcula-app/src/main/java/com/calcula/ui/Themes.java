package com.calcula.ui;

import java.util.List;
import java.util.Locale;

import javafx.application.Application;
import javafx.scene.Scene;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;

/**
 * The two Rule &amp; Plate themes: {@link #PLATE} (light) and {@link #SLAB} (dark).
 *
 * <h2>Why this is two stylesheets and not one</h2>
 *
 * <p>AtlantaFX's Primer stays the <b>user-agent</b> stylesheet and keeps styling every standard
 * control. On top of it goes a small <b>scene</b> stylesheet that redefines the semantic
 * {@code -color-*} tokens Primer resolves against. A scene stylesheet outranks the user-agent one,
 * so the whole window re-colours without a single component rule being restated.
 *
 * <p>The alternative — authoring a complete AtlantaFX theme — means carrying ~4800 lines of
 * component CSS that has nothing to do with Calcula, and re-vendoring it on every AtlantaFX bump.
 * The token block is about seventy lines and is the only part that is actually ours.
 *
 * <h2>Order matters</h2>
 *
 * <p>{@code app.css} references the tokens, so it has to be applied <i>after</i> the theme sheet
 * that defines them. {@link #apply(Scene, Themes)} owns that ordering; callers should not add
 * either sheet by hand.
 */
public enum Themes {

    /** Light. Cool paper, graphite ink. */
    PLATE("Plate", "themes/plate.css", false),

    /** Dark. Cool slate, chalk ink. */
    SLAB("Slab", "themes/slab.css", true);

    /** The theme a fresh install starts on. */
    public static final Themes DEFAULT = SLAB;

    private final String display;
    private final String sheet;
    private final boolean dark;

    Themes(String display, String sheet, boolean dark) {
        this.display = display;
        this.sheet = sheet;
        this.dark = dark;
    }

    /** How it appears to a user. */
    public String display() {
        return display;
    }

    /** Whether this theme sits on a dark ground. Drives the Primer base underneath it. */
    public boolean dark() {
        return dark;
    }

    /**
     * The theme with this name, case-insensitively, or {@link #DEFAULT} for anything unrecognised.
     *
     * <p>Deliberately total rather than throwing: the name arrives from persisted settings, and a
     * config file written by a newer build naming a theme this one has never heard of should
     * downgrade to something usable, not refuse to open a window.
     */
    public static Themes byName(String name) {
        if (name != null) {
            for (Themes t : values()) {
                if (t.name().equalsIgnoreCase(name) || t.display.equalsIgnoreCase(name)) {
                    return t;
                }
            }
        }
        return DEFAULT;
    }

    /** Every theme, in presentation order. */
    public static List<Themes> all() {
        return List.of(values());
    }

    /**
     * Apply a theme to a scene: Primer as the user-agent base, then our token block, then
     * {@code app.css}.
     *
     * <p>Safe to call repeatedly — the scene's own stylesheets are cleared first, so switching
     * themes at runtime does not leave the previous palette layered underneath.
     */
    public static void apply(Scene scene, Themes theme) {
        // BEFORE any stylesheet: a family that is not registered when the sheet naming it is parsed
        // does not resolve, and JavaFX substitutes silently rather than complaining.
        Fonts.load();
        Application.setUserAgentStylesheet(
                theme.dark() ? new PrimerDark().getUserAgentStylesheet() : new PrimerLight().getUserAgentStylesheet());

        scene.getStylesheets().clear();
        scene.getStylesheets().add(resource(theme.sheet));
        scene.getStylesheets().add(resource("app.css"));
        // Last, so the root face is the final word on it. It is a scene sheet rather than an app.css
        // rule because every dialog and popup has its own Scene, and because a scene sheet survives
        // the setUserAgentStylesheet that a theme switch performs.
        scene.getStylesheets().add(resource("ui-font.css"));
    }

    private static String resource(String name) {
        var url = Themes.class.getResource("/com/calcula/styles/" + name);
        if (url == null) {
            // A missing stylesheet is a packaging fault, not a user error, and it degrades to an
            // unreadable window rather than an exception. Say so loudly here instead.
            throw new IllegalStateException("stylesheet not on the module path: " + name);
        }
        return url.toExternalForm();
    }

    /** The lower-cased enum name, for persisting. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
