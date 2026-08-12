package com.calcula.ui;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A chord must not also type its letter.
 *
 * <p>The platform delivers KEY_PRESSED and then KEY_TYPED for one keystroke, and <b>consuming the
 * first does not stop the second</b> — a text field inserts on the typed event. So a chord could run
 * its command and leave its own letter behind in the input line: Alt+F moved the caret by a word and
 * then typed an f into the place it had moved to.
 *
 * <p>These fire BOTH events, in that order, because a test that fires only the press passes against
 * the broken code. That is exactly why the bug shipped.
 */
@Tag("fx")
class ChordTypingFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @BeforeEach
    void isolate() throws Exception {
        FxTestSupport.freshConfigDir();
    }

    /** A window whose input line is in a real scene, so events reach its filters. */
    private static CalcWindow windowed() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> {
            Region root = window.getRoot();
            Scene scene = new Scene(root, 900, 600);
            Themes.apply(scene, Themes.byName(window.settings().themeId()));
            root.applyCss();
            root.layout();
        });
        return window;
    }

    /**
     * One keystroke, as the platform sends it: the press, then the character.
     *
     * <p>{@code character} is what the OS says the key produced — on macOS an Option combination may
     *report a different glyph, so this takes it rather than deriving it.
     */
    private static void keystroke(CalcWindow window, KeyCode code, String character, boolean alt, boolean control)
            throws Exception {
        FxTestSupport.runOnFx(() -> {
            Event.fireEvent(
                    window.inputField(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, control, alt, false));
            Event.fireEvent(
                    window.inputField(),
                    new KeyEvent(KeyEvent.KEY_TYPED, character, "", KeyCode.UNDEFINED, false, control, alt, false));
        });
    }

    private static void type(CalcWindow window, String text) throws Exception {
        FxTestSupport.runOnFx(() -> {
            window.inputField().setText(text);
            window.inputField().positionCaret(text.length());
        });
    }

    @Test
    void altFMovesByAWordWithoutTypingAnF() throws Exception {
        CalcWindow window = windowed();
        type(window, "alpha beta");
        FxTestSupport.runOnFx(() -> window.inputField().positionCaret(0));

        keystroke(window, KeyCode.F, "f", true, false);

        assertEquals("alpha beta", window.inputField().getText(), "the chord typed its own letter");
        assertEquals(5, window.inputField().getCaretPosition(), "the caret did not move by a word");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void altBIsTheSameInReverse() throws Exception {
        CalcWindow window = windowed();
        type(window, "alpha beta");
        keystroke(window, KeyCode.B, "b", true, false);
        assertEquals("alpha beta", window.inputField().getText());
        assertEquals(6, window.inputField().getCaretPosition());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aMacOptionCharacterIsSwallowedToo() throws Exception {
        // macOS composes Option+f into ƒ. Whatever the platform decides the key produced, it must not
        // reach the field once the press has been handled as a chord.
        CalcWindow window = windowed();
        type(window, "alpha beta");
        FxTestSupport.runOnFx(() -> window.inputField().positionCaret(0));
        keystroke(window, KeyCode.F, "ƒ", true, false);
        assertEquals("alpha beta", window.inputField().getText());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void anOrdinaryLetterStillReachesTheField() throws Exception {
        // The other half, and the one that matters more: swallowing must not leak past the chord it
        // belongs to, or the input line stops accepting text.
        //
        // Asserted on whether the event was CONSUMED rather than on the resulting text. Whether the
        // field's skin actually inserts depends on it being laid out, which differs between running
        // this class alone and running it in the suite — and a test that changes its mind is worse
        // than a narrower one. Consumption is the part this code decides.
        CalcWindow window = windowed();
        type(window, "");
        keystroke(window, KeyCode.F, "f", true, false); // a chord first, so the flag has been set

        boolean[] consumed = {false};
        FxTestSupport.runOnFx(() -> {
            Event.fireEvent(
                    window.inputField(),
                    new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.X, false, false, false, false));
            KeyEvent typed = new KeyEvent(KeyEvent.KEY_TYPED, "x", "", KeyCode.UNDEFINED, false, false, false, false);
            Event.fireEvent(window.inputField(), typed);
            consumed[0] = typed.isConsumed();
        });
        org.junit.jupiter.api.Assertions.assertFalse(consumed[0], "an ordinary letter was swallowed");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aChordThatProducesNoCharacterDoesNotEatTheNextOne() throws Exception {
        // C-a sends no typed event on most platforms, so the flag it sets has to be cleared by the
        // NEXT press rather than by a typed event that never comes.
        CalcWindow window = windowed();
        type(window, "alpha beta");
        FxTestSupport.runOnFx(() -> {
            Event.fireEvent(
                    window.inputField(),
                    new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.A, false, true, false, false));
            // no KEY_TYPED at all, as the platform would do
        });
        assertEquals(0, window.inputField().getCaretPosition(), "C-a did not go to the start");

        boolean[] consumed = {false};
        FxTestSupport.runOnFx(() -> {
            KeyEvent pressed = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.Z, false, false, false, false);
            Event.fireEvent(window.inputField(), pressed);
            KeyEvent typed = new KeyEvent(KeyEvent.KEY_TYPED, "z", "", KeyCode.UNDEFINED, false, false, false, false);
            Event.fireEvent(window.inputField(), typed);
            consumed[0] = typed.isConsumed();
        });
        org.junit.jupiter.api.Assertions.assertFalse(consumed[0], "a stale flag ate an ordinary character");
        FxTestSupport.runOnFx(window::dispose);
    }
}
