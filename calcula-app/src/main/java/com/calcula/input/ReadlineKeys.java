package com.calcula.input;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The readline editing keys, as pure text arithmetic.
 *
 * <p>Every shell, every Emacs buffer and every macOS text field understands {@code C-a} and {@code
 * C-e}, and an application whose entire premise is the keyboard cannot be the one place they do
 * nothing. JavaFX implements none of them: its {@code TextField} knows Home and End, and leaves the
 * rest to the platform, which on macOS means the OS text system rather than the toolkit.
 *
 * <p>Pure on purpose. What a key does to a string and a caret is decidable without a toolkit, so all
 * of it is testable without one — {@link #apply} takes text and a caret and returns text and a caret,
 * and the JavaFX layer is left as the part that reads and writes a control.
 *
 * <p>The input line is single-line, so "line" and "whole text" are the same thing here. That is why
 * {@code C-a} and {@code C-e} are simply the ends.
 */
public final class ReadlineKeys {

    /** Text and where the caret sits in it. */
    public record Edit(String text, int caret, String killed) {

        /** Nothing was removed, so nothing goes on the kill ring. */
        static Edit of(String text, int caret) {
            return new Edit(text, caret, null);
        }
    }

    /** Chord to action, in the order they are worth reading. */
    private static final Map<String, String> CHORDS = new LinkedHashMap<>();

    static {
        CHORDS.put("C-a", "lineStart");
        CHORDS.put("C-e", "lineEnd");
        CHORDS.put("C-f", "forwardChar");
        CHORDS.put("C-b", "backwardChar");
        CHORDS.put("M-f", "forwardWord");
        CHORDS.put("M-b", "backwardWord");
        CHORDS.put("C-d", "deleteChar");
        CHORDS.put("M-d", "killWordForward");
        CHORDS.put("C-k", "killToEnd");
        CHORDS.put("C-u", "killToStart");
        CHORDS.put("C-w", "killWordBackward");
        CHORDS.put("M-DEL", "killWordBackward");
        CHORDS.put("C-t", "transposeChars");
    }

    private ReadlineKeys() {}

    /** The action a chord performs on the input line, or null when it is not one of ours. */
    public static String actionFor(String chord) {
        return CHORDS.get(chord);
    }

    /** Every chord handled here, for a help sheet or a test. */
    public static Map<String, String> chords() {
        return Map.copyOf(CHORDS);
    }

    /**
     * Apply an action, returning the new text and caret.
     *
     * <p>Returns null when the action would change nothing, so the caller can leave the key
     * unconsumed — a {@code C-f} at the end of the line should not swallow itself.
     */
    public static Edit apply(String action, String text, int caret) {
        int at = Math.clamp(caret, 0, text.length());
        return switch (action) {
            case "lineStart" -> at == 0 ? null : Edit.of(text, 0);
            case "lineEnd" -> at == text.length() ? null : Edit.of(text, text.length());
            case "forwardChar" -> at == text.length() ? null : Edit.of(text, at + 1);
            case "backwardChar" -> at == 0 ? null : Edit.of(text, at - 1);
            case "forwardWord" -> at == text.length() ? null : Edit.of(text, wordEnd(text, at));
            case "backwardWord" -> at == 0 ? null : Edit.of(text, wordStart(text, at));
            case "deleteChar" -> at == text.length() ? null : Edit.of(cut(text, at, at + 1), at);
            case "killToEnd" -> at == text.length() ? null : killed(text, at, text.length(), at);
            case "killToStart" -> at == 0 ? null : killed(text, 0, at, 0);
            case "killWordForward" -> at == text.length() ? null : killed(text, at, wordEnd(text, at), at);
            case "killWordBackward" -> at == 0 ? null : killed(text, wordStart(text, at), at, wordStart(text, at));
            case "transposeChars" -> transpose(text, at);
            default -> null;
        };
    }

    /** An edit that removes a span, remembering what it removed so the caller can put it on the ring. */
    private static Edit killed(String text, int from, int to, int caret) {
        return from == to ? null : new Edit(cut(text, from, to), caret, text.substring(from, to));
    }

    private static String cut(String text, int from, int to) {
        return text.substring(0, from) + text.substring(to);
    }

    /**
     * Swap the two characters around the caret — readline's {@code C-t}.
     *
     * <p>At the very end of the line it swaps the last two and leaves the caret there, which is what
     * readline does and what makes it useful for fixing a transposition you have just typed.
     */
    private static Edit transpose(String text, int at) {
        if (text.length() < 2) {
            return null;
        }
        int right = at >= text.length() ? text.length() - 1 : at;
        if (right < 1) {
            return null;
        }
        String swapped =
                text.substring(0, right - 1) + text.charAt(right) + text.charAt(right - 1) + text.substring(right + 1);
        return Edit.of(swapped, Math.min(right + 1, swapped.length()));
    }

    /**
     * The end of the word at or after {@code from}.
     *
     * <p>Skips any run of non-word characters first, then the word — so from inside {@code sin(x)} a
     * forward-word lands past {@code x} rather than stopping on every bracket.
     */
    static int wordEnd(String text, int from) {
        int i = from;
        while (i < text.length() && !isWord(text.charAt(i))) {
            i++;
        }
        while (i < text.length() && isWord(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /** The start of the word at or before {@code from}, mirroring {@link #wordEnd}. */
    static int wordStart(String text, int from) {
        int i = from;
        while (i > 0 && !isWord(text.charAt(i - 1))) {
            i--;
        }
        while (i > 0 && isWord(text.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    /**
     * What counts as part of a word.
     *
     * <p>Letters, digits and underscore — deliberately NOT the dot, so a word motion inside
     * {@code 1.5} stops at the point rather than treating the number as one blob.
     */
    private static boolean isWord(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
