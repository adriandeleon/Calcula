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
 * @param trailSize point size for the trail, sized separately because it is a log rather than the
 *     working surface — people keep it small to see more of it, or large to actually read it
 */
public record Settings(
        String themeId,
        String inputModel,
        Modes modes,
        double mathSize,
        double trailSize,
        double trailSplit,
        boolean trailShown) {

    /**
     * Bumped whenever the shape changes, so an older build never silently reinterprets a newer file.
     * A file claiming a higher version is set aside rather than parsed — see {@link SettingsStore}.
     */
    public static final int SCHEMA_VERSION = 3;

    public static final String ALGEBRAIC = "algebraic";
    public static final String RPN = "rpn";

    public static final double MIN_MATH_SIZE = 9;
    public static final double MAX_MATH_SIZE = 48;

    public static final double MIN_TRAIL_SIZE = 8;
    public static final double MAX_TRAIL_SIZE = 32;

    /**
     * How much of the window the trail may take.
     *
     * <p>Bounded at both ends rather than left free. All the way open there is no stack left to read,
     * and a sliver is a column that costs its own width in border and shows nothing — if someone wants
     * the trail gone, the answer is to close it, which is a different state and is remembered as one.
     */
    public static final double MIN_TRAIL_SPLIT = 0.1;

    public static final double MAX_TRAIL_SPLIT = 0.6;

    /** Wide enough for a typical result line without taking the window over. */
    public static final double DEFAULT_TRAIL_SPLIT = 0.28;

    /**
     * Algebraic entry, because it is the gentler of the two to meet first.
     *
     * <p>This is where the question of which model is the default finally gets answered — as a
     * preference rather than a hardcoded choice, which is the honest resolution: the two readers are
     * equally supported, and which one someone wants is not something the program can know.
     */
    public static final Settings DEFAULTS =
            new Settings("slab", ALGEBRAIC, Modes.DEFAULTS, 17, 11, DEFAULT_TRAIL_SPLIT, true);

    public Settings {
        themeId = themeId == null || themeId.isBlank() ? DEFAULTS_THEME : themeId.trim();
        inputModel = RPN.equalsIgnoreCase(inputModel) ? RPN : ALGEBRAIC;
        modes = modes == null ? Modes.DEFAULTS : modes;
        // Clamped rather than rejected: a hand-edited settings file should not stop the app opening,
        // and a 500-point stack entry is indistinguishable from a broken window.
        mathSize = Math.clamp(mathSize, MIN_MATH_SIZE, MAX_MATH_SIZE);
        trailSize = Math.clamp(trailSize, MIN_TRAIL_SIZE, MAX_TRAIL_SIZE);
        trailSplit = Math.clamp(trailSplit, MIN_TRAIL_SPLIT, MAX_TRAIL_SPLIT);
    }

    private static final String DEFAULTS_THEME = "slab";

    public boolean isRpn() {
        return RPN.equals(inputModel);
    }

    public Settings withTheme(String id) {
        return new Settings(id, inputModel, modes, mathSize, trailSize, trailSplit, trailShown);
    }

    public Settings withInputModel(String model) {
        return new Settings(themeId, model, modes, mathSize, trailSize, trailSplit, trailShown);
    }

    public Settings withModes(Modes newModes) {
        return new Settings(themeId, inputModel, newModes, mathSize, trailSize, trailSplit, trailShown);
    }

    public Settings withMathSize(double size) {
        return new Settings(themeId, inputModel, modes, size, trailSize, trailSplit, trailShown);
    }

    /**
     * Where the divider sits when the trail is open.
     *
     * <p>Kept separately from {@link #trailShown} so closing the trail does not forget the width it
     * had. Reopening it to a default the user had already moved away from is a small forgetting that
     * happens every session.
     */
    public Settings withTrailSplit(double fraction) {
        return new Settings(themeId, inputModel, modes, mathSize, trailSize, fraction, trailShown);
    }

    public Settings withTrailShown(boolean shown) {
        return new Settings(themeId, inputModel, modes, mathSize, trailSize, trailSplit, shown);
    }

    public Settings withTrailSize(double size) {
        return new Settings(themeId, inputModel, modes, mathSize, size, trailSplit, trailShown);
    }
}
