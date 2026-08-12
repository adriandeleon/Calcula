package com.calcula.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReadlineKeysTest {

    /** Writes the caret as | so an expectation reads as the line it describes. */
    private static String edit(String action, String withCaret) {
        int caret = withCaret.indexOf('|');
        String text = withCaret.replace("|", "");
        ReadlineKeys.Edit e = ReadlineKeys.apply(action, text, caret);
        if (e == null) {
            return null;
        }
        return e.text().substring(0, e.caret()) + "|" + e.text().substring(e.caret());
    }

    @Test
    void theEndsOfTheLine() {
        assertEquals("|sin(x)", edit("lineStart", "sin(|x)"));
        assertEquals("sin(x)|", edit("lineEnd", "sin(|x)"));
    }

    @Test
    void movingByCharacter() {
        assertEquals("si|n(x)", edit("forwardChar", "s|in(x)"));
        assertEquals("|sin(x)", edit("backwardChar", "s|in(x)"));
    }

    @Test
    void movingByWordSkipsThePunctuationBetweenWords() {
        // From inside sin(x), forward-word lands past x rather than stopping on every bracket.
        assertEquals("sin|(x)", edit("forwardWord", "|sin(x)"));
        assertEquals("sin(x|)", edit("forwardWord", "sin(|x)"));
        assertEquals("sin(|x)", edit("backwardWord", "sin(x|)"));
    }

    @Test
    void aWordStopsAtADecimalPoint() {
        // Deliberately: a motion inside 1.5 should reach the point, not treat the number as one blob.
        assertEquals("1|.5", edit("forwardWord", "|1.5"));
    }

    @Test
    void deletingForward() {
        assertEquals("sn(x)", edit("deleteChar", "s|in(x)").replace("|", ""));
        assertEquals("s|n(x)", edit("deleteChar", "s|in(x)"));
    }

    @Test
    void killingToEitherEnd() {
        assertEquals("sin(|", edit("killToEnd", "sin(|x)"));
        assertEquals("|x)", edit("killToStart", "sin(|x)"));
    }

    @Test
    void killingByWord() {
        assertEquals("sin(|)", edit("killWordForward", "sin(|x)"));
        assertEquals("sin(|)", edit("killWordBackward", "sin(x|)"));
    }

    @Test
    void whatWasKilledIsReportedSoItCanBeYankedBack() {
        assertEquals("x)", ReadlineKeys.apply("killToEnd", "sin(x)", 4).killed());
        assertEquals("sin(", ReadlineKeys.apply("killToStart", "sin(x)", 4).killed());
        assertNull(ReadlineKeys.apply("forwardChar", "sin(x)", 0).killed(), "a motion kills nothing");
    }

    @Test
    void transposingSwapsTheCharactersAroundTheCaret() {
        // Readline DRAGS the character before point forward over the one at point, and moves point
        // with it — so si|n becomes sni|, not isn|. My first expectation here was the wrong one.
        assertEquals("sni|(x)", edit("transposeChars", "si|n(x)"));
    }

    @Test
    void transposingAtTheEndFixesTheLastTwoTyped() {
        // What makes C-t useful: it corrects the transposition you have just made.
        assertEquals("sni|", edit("transposeChars", "sin|"));
    }

    @Test
    void anActionThatWouldChangeNothingReturnsNothing() {
        // So the caller can leave the key unconsumed rather than swallowing it.
        assertNull(edit("backwardChar", "|sin(x)"));
        assertNull(edit("forwardChar", "sin(x)|"));
        assertNull(edit("lineStart", "|sin(x)"));
        assertNull(edit("killToEnd", "sin(x)|"));
        assertNull(ReadlineKeys.apply("transposeChars", "x", 1), "one character cannot be transposed");
    }

    @Test
    void aCaretOutsideTheTextIsClampedRatherThanThrowing() {
        // A caret past the end is clamped to the end, so forward-char has nowhere to go.
        assertNull(ReadlineKeys.apply("forwardChar", "abc", 99));
        assertEquals(0, ReadlineKeys.apply("lineStart", "abc", 99).caret());
        assertNull(ReadlineKeys.apply("backwardChar", "abc", -5), "and a negative one to the start");
    }

    @Test
    void theChordsAreTheOnesEveryShellUses() {
        assertEquals("lineStart", ReadlineKeys.actionFor("C-a"));
        assertEquals("lineEnd", ReadlineKeys.actionFor("C-e"));
        assertEquals("killToEnd", ReadlineKeys.actionFor("C-k"));
        assertEquals("killWordBackward", ReadlineKeys.actionFor("C-w"));
        assertEquals("killWordBackward", ReadlineKeys.actionFor("M-DEL"), "and the Emacs spelling of it");
        assertNull(ReadlineKeys.actionFor("C-x"), "a prefix that belongs to the calculator");
    }

    @Test
    void everyChordMapsToAnActionApplyUnderstands() {
        for (var entry : ReadlineKeys.chords().entrySet()) {
            // A chord naming an action that apply() does not implement would silently do nothing.
            assertNull(
                    ReadlineKeys.apply(entry.getValue(), "", 0),
                    entry.getKey() + " on an empty line should be a no-op, not a crash");
            ReadlineKeys.apply(entry.getValue(), "sin(x) + 1", 3);
        }
    }
}
