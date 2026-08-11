package com.calcula.ui;

import java.util.Locale;
import java.util.Map;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * Translates a JavaFX key event into the chord token {@link com.calcula.key.KeyDispatcher} resolves.
 *
 * <p>The one rule that matters: <b>an unmodified printable key is not a chord</b>. It returns null, so
 * the keystroke reaches the text field and types a character. Without that the echo area could never be
 * typed into, because every letter would be a command.
 *
 * <p>That is a real difference from Emacs Calc, where the stack has focus and letters are commands. It
 * follows from the input model being undecided: until that is settled, one widget has to accept both,
 * and the safe division is that commands carry a modifier or are named keys.
 *
 * <p>Modifiers are emitted in a fixed order — {@code C- M- Cmd- S-} — so a binding written one way
 * always matches. Order-insensitive comparison is the alternative, and it is slower and easy to get
 * subtly wrong.
 */
public final class Chords {

    /** Keys that are a chord even with no modifier held, because they cannot type a character. */
    private static final Map<KeyCode, String> NAMED = Map.ofEntries(
            Map.entry(KeyCode.BACK_SPACE, "DEL"),
            Map.entry(KeyCode.DELETE, "Delete"),
            Map.entry(KeyCode.TAB, "TAB"),
            Map.entry(KeyCode.ENTER, "RET"),
            Map.entry(KeyCode.ESCAPE, "ESC"),
            Map.entry(KeyCode.SPACE, "SPC"),
            Map.entry(KeyCode.UP, "Up"),
            Map.entry(KeyCode.DOWN, "Down"),
            Map.entry(KeyCode.LEFT, "Left"),
            Map.entry(KeyCode.RIGHT, "Right"),
            Map.entry(KeyCode.HOME, "Home"),
            Map.entry(KeyCode.END, "End"),
            Map.entry(KeyCode.PAGE_UP, "PageUp"),
            Map.entry(KeyCode.PAGE_DOWN, "PageDown"));

    private Chords() {}

    /**
     * The chord for this event, or null when it is ordinary typing and should be left alone.
     *
     * <p>A modifier key pressed by itself is also null: {@code Control} on its own is the start of a
     * chord, not one.
     */
    public static String chordFor(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code == null || code == KeyCode.UNDEFINED || code.isModifierKey()) {
            return null;
        }
        boolean modified = event.isControlDown() || event.isAltDown() || event.isMetaDown();
        String name = NAMED.get(code);
        if (name == null) {
            if (!modified) {
                return null; // a printable key with nothing held: let it type
            }
            name = keyName(code);
        }
        StringBuilder token = new StringBuilder(8);
        if (event.isControlDown()) {
            token.append("C-");
        }
        if (event.isAltDown()) {
            token.append("M-");
        }
        if (event.isMetaDown()) {
            token.append("Cmd-");
        }
        // Shift only counts alongside another modifier. On its own it is how a capital is typed, which
        // is not a chord, and treating it as one would make C-A and C-S-a two different bindings by
        // accident.
        if (event.isShiftDown() && modified) {
            token.append("S-");
        }
        return token.append(name).toString();
    }

    private static String keyName(KeyCode code) {
        String name = code.getName();
        // Single characters read better lower-cased (`C-x`, not `C-X`); longer names — F1, Insert —
        // keep their own capitalisation.
        return name.length() == 1 ? name.toLowerCase(Locale.ROOT) : name;
    }
}
