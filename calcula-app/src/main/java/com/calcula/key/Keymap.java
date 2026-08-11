package com.calcula.key;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Chord sequences to command ids.
 *
 * <p>A chord is a token like {@code a}, {@code C-x} or {@code M-RET}; a sequence is chords joined by
 * spaces, as in Calc's {@code a d} for differentiate or {@code u c} for convert units. Prefixes are not
 * declared — they are implied by any binding having more chords after the ones typed so far, so adding
 * {@code a f} automatically makes {@code a} a prefix.
 */
public final class Keymap {

    /** Sorted, so prefix lookup can use a range scan rather than walking every binding. */
    private final TreeMap<String, String> bindings = new TreeMap<>();

    public void bind(String sequence, String commandId) {
        if (sequence == null || sequence.isBlank()) {
            throw new IllegalArgumentException("blank chord sequence");
        }
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException("blank command id for " + sequence);
        }
        bindings.put(sequence.trim(), commandId);
    }

    public void unbind(String sequence) {
        bindings.remove(sequence);
    }

    /** The command bound exactly to this sequence, or null. */
    public String commandFor(String sequence) {
        return bindings.get(sequence);
    }

    /**
     * True when something longer starts with this sequence, so the dispatcher should wait for more.
     *
     * <p>A sequence can be both bound AND a prefix — {@code a} could run something while {@code a d}
     * runs another. The dispatcher decides what to do about that; this only reports the fact.
     */
    public boolean isPrefix(String sequence) {
        String withSeparator = sequence + " ";
        String next = bindings.ceilingKey(withSeparator);
        return next != null && next.startsWith(withSeparator);
    }

    /** All bindings, in sorted order. */
    public Map<String, String> bindings() {
        return new LinkedHashMap<>(bindings);
    }

    /**
     * Command id to the sequence to <em>show</em> for it, in a menu or a palette.
     *
     * <p>A command can have several bindings, and this picks the one worth teaching: the <b>fewest
     * chords</b>, ties broken by shorter text and then alphabetically so the answer is stable. Undo is
     * bound to both {@code C-z} and {@code C-x u}, and taking whichever came first in sorted order
     * advertised {@code C-x u} — technically true, and the wrong thing to tell someone, since the
     * one-chord binding is the whole reason it exists.
     *
     * <p>Display only. Every binding still runs; this decides which one the interface teaches.
     */
    public Map<String, String> invert() {
        Map<String, String> inverted = new LinkedHashMap<>();
        bindings.forEach((sequence, id) -> inverted.merge(id, sequence, Keymap::moreTeachable));
        return inverted;
    }

    /** The more teachable of two bindings for the same command. */
    private static String moreTeachable(String a, String b) {
        int chords = Integer.compare(chordCount(a), chordCount(b));
        if (chords != 0) {
            return chords < 0 ? a : b;
        }
        int length = Integer.compare(a.length(), b.length());
        if (length != 0) {
            return length < 0 ? a : b;
        }
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static int chordCount(String sequence) {
        int chords = 1;
        for (int i = 0; i < sequence.length(); i++) {
            if (sequence.charAt(i) == ' ') {
                chords++;
            }
        }
        return chords;
    }

    public int size() {
        return bindings.size();
    }
}
