package com.calcula.ui;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * No toolkit needed: a {@code KeyEvent} is an ordinary object, so the translation can be driven
 * directly with synthetic events.
 */
class ChordsTest {

    private static KeyEvent key(KeyCode code, boolean control, boolean alt, boolean shift, boolean meta) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, control, alt, meta);
    }

    private static KeyEvent plain(KeyCode code) {
        return key(code, false, false, false, false);
    }

    @Test
    void anUnmodifiedPrintableKeyIsNotAChord() {
        // The property the whole echo area depends on: without it every letter would be a command
        // and the field could never be typed into.
        assertNull(Chords.chordFor(plain(KeyCode.A)));
        assertNull(Chords.chordFor(plain(KeyCode.DIGIT5)));
        assertNull(Chords.chordFor(key(KeyCode.A, false, false, true, false)), "shift alone is how a capital is typed");
    }

    @Test
    void namedKeysAreChordsEvenUnmodified() {
        assertEquals("RET", Chords.chordFor(plain(KeyCode.ENTER)));
        assertEquals("DEL", Chords.chordFor(plain(KeyCode.BACK_SPACE)));
        assertEquals("TAB", Chords.chordFor(plain(KeyCode.TAB)));
        assertEquals("ESC", Chords.chordFor(plain(KeyCode.ESCAPE)));
        assertEquals("Up", Chords.chordFor(plain(KeyCode.UP)));
    }

    @Test
    void modifiersUseAFixedOrderSoBindingsAlwaysMatch() {
        assertEquals("C-x", Chords.chordFor(key(KeyCode.X, true, false, false, false)));
        assertEquals("M-i", Chords.chordFor(key(KeyCode.I, false, true, false, false)));
        assertEquals("C-M-z", Chords.chordFor(key(KeyCode.Z, true, true, false, false)));
        assertEquals("C-S-z", Chords.chordFor(key(KeyCode.Z, true, false, true, false)));
        assertEquals("Cmd-s", Chords.chordFor(key(KeyCode.S, false, false, false, true)));
    }

    @Test
    void aBareLetterBecomesAChordWhileAPrefixIsHeld() {
        // Both halves of the rule. Without the second, the letter that completes C-x u or M-m r goes
        // into the text field instead and the prefix hangs — a binding that silently does not exist.
        assertNull(Chords.chordFor(plain(KeyCode.U), false));
        assertEquals("u", Chords.chordFor(plain(KeyCode.U), true));
        assertEquals("5", Chords.chordFor(plain(KeyCode.DIGIT5), true));
    }

    @Test
    void continuingDoesNotChangeHowAModifiedOrNamedKeyReads() {
        // A prefix followed by a real chord is ordinary: C-x C-s must not become something else.
        assertEquals("C-s", Chords.chordFor(key(KeyCode.S, true, false, false, false), true));
        assertEquals("RET", Chords.chordFor(plain(KeyCode.ENTER), true));
        assertNull(Chords.chordFor(plain(KeyCode.CONTROL), true), "a modifier alone is still not a chord");
    }

    @Test
    void modifierKeysPressedAloneAreNotChords() {
        // Holding Control is the start of a chord, not one.
        assertNull(Chords.chordFor(plain(KeyCode.CONTROL)));
        assertNull(Chords.chordFor(plain(KeyCode.SHIFT)));
        assertNull(Chords.chordFor(plain(KeyCode.ALT)));
    }

    @Test
    void singleCharacterNamesAreLowercasedAndLongerOnesAreNot() {
        assertEquals("C-x", Chords.chordFor(key(KeyCode.X, true, false, false, false)));
        assertEquals("C-M-TAB", Chords.chordFor(key(KeyCode.TAB, true, true, false, false)));
    }
}
