package com.calcula.config;

import com.calcula.machine.Modes;

/**
 * What the calculator remembers between sessions.
 *
 * <p>Deliberately small, and deliberately not the same thing as {@link Modes}. Modes are session state
 * that lives on the stack machine and moves with undo; these are the values a NEW session starts from.
 * Conflating them would mean a mode flipped for one calculation quietly became the default, and that
 * undoing it did not put the preference back.
 *
 * <p>The theme and the entry model are held as ids rather than as their own types, so this package
 * stays below the toolkit: {@code ui.Themes} resolves the one and the reader factory the other. A
 * record here holding a {@code Themes} would make the settings file's schema depend on a JavaFX enum.
 *
 * @param themeId {@code plate} or {@code slab}; anything unrecognised falls back to the default theme
 * @param inputModel {@code algebraic} or {@code rpn}
 * @param modes the angle unit, precision and so on that a new session begins with
 * @param mathSize point size for typeset stack entries
 */
public record Settings(String themeId, String inputModel, Modes modes, double mathSize) {

    /**
     * Bumped whenever the shape changes, so an older build never silently reinterprets a newer file.
     * A file claiming a higher version is set aside rather than parsed — see {@link SettingsStore}.
     */
    public static final int SCHEMA_VERSION = 1;

    public static final String ALGEBRAIC = "algebraic";
    public static final String RPN = "rpn";

    public static final double MIN_MATH_SIZE = 9;
    public static final double MAX_MATH_SIZE = 48;

    /**
     * Algebraic entry, because it is the gentler of the two to meet first.
     *
     * <p>This is where the question of which model is the default finally gets answered — as a
     * preference rather than a hardcoded choice, which is the honest resolution: the two readers are
     * equally supported, and which one someone wants is not something the program can know.
     */
    public static final Settings DEFAULTS = new Settings("slab", ALGEBRAIC, Modes.DEFAULTS, 17);

    public Settings {
        themeId = themeId == null || themeId.isBlank() ? DEFAULTS_THEME : themeId.trim();
        inputModel = RPN.equalsIgnoreCase(inputModel) ? RPN : ALGEBRAIC;
        modes = modes == null ? Modes.DEFAULTS : modes;
        // Clamped rather than rejected: a hand-edited settings file should not stop the app opening,
        // and a 500-point stack entry is indistinguishable from a broken window.
        mathSize = Math.clamp(mathSize, MIN_MATH_SIZE, MAX_MATH_SIZE);
    }

    private static final String DEFAULTS_THEME = "slab";

    public boolean isRpn() {
        return RPN.equals(inputModel);
    }

    public Settings withTheme(String id) {
        return new Settings(id, inputModel, modes, mathSize);
    }

    public Settings withInputModel(String model) {
        return new Settings(themeId, model, modes, mathSize);
    }

    public Settings withModes(Modes newModes) {
        return new Settings(themeId, inputModel, newModes, mathSize);
    }

    public Settings withMathSize(double size) {
        return new Settings(themeId, inputModel, modes, size);
    }
}
