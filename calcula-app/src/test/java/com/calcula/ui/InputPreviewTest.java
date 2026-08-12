package com.calcula.ui;

import com.calcula.parse.Formatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the strip above the input decides to show. Pure — no toolkit needed to ask these. */
class InputPreviewTest {

    private static InputPreview.Preview algebraic(String line) {
        return InputPreview.of(line, "algebraic");
    }

    @Test
    void aReadableLineIsSetAsMathematics() {
        InputPreview.Preview p = algebraic("1/2 + 1/3");
        assertNotNull(p.parsed());
        assertNull(p.error());
        assertEquals("1/2 + 1/3", Formatter.format(p.parsed()));
    }

    /**
     * The case the strip exists for: two lines four characters apart that mean very different things,
     * and no way to tell which one was typed except by pressing Enter and reading the answer
     * backwards.
     */
    @Test
    void precedenceIsVisibleBeforeItIsCommittedTo() {
        assertEquals("1/2 + 1/3", Formatter.format(algebraic("1/2 + 1/3").parsed()));
        assertEquals("1/(2 + 1)/3", Formatter.format(algebraic("1/(2 + 1)/3").parsed()));
    }

    @Test
    void anEmptyLineSaysNothingAndTakesNoRoom() {
        assertTrue(algebraic("").isQuiet());
        assertTrue(algebraic("   ").isQuiet());
        assertTrue(algebraic(null).isQuiet());
    }

    @Test
    void aSyntaxErrorIsReportedWithWhereItIs() {
        InputPreview.Preview p = algebraic("1 +");
        assertNull(p.parsed());
        assertNotNull(p.error());
        assertFalse(p.isQuiet(), "an error is something to say, not silence");
        assertTrue(InputPreview.message(p).contains("at "), InputPreview.message(p));
    }

    /**
     * Half-typed input is the normal state of an input line, so the strip has to be comfortable with
     * it rather than treating it as exceptional.
     */
    @Test
    void everyPrefixOfARealExpressionIsSafeToAskAbout() {
        String whole = "1/2 + sin(x)^2";
        for (int i = 1; i <= whole.length(); i++) {
            String prefix = whole.substring(0, i);
            InputPreview.Preview p = algebraic(prefix);
            assertTrue(
                    p.isQuiet() || p.parsed() != null || p.error() != null, "no verdict for prefix <" + prefix + ">");
        }
    }

    /**
     * RPN gets no preview, deliberately. A line there is a sequence of operations, not one
     * expression, so there is nothing single to set — and the stack is already the display for what
     * RPN is doing.
     */
    @Test
    void rpnIsLeftAlone() {
        assertTrue(InputPreview.of("5 3 -", "rpn").isQuiet());
        assertTrue(InputPreview.of("1/2 + 1/3", "rpn").isQuiet(), "even a line that would parse");
    }

    @Test
    void theMessageIsEmptyWhenThereIsNoError() {
        assertEquals("", InputPreview.message(algebraic("1 + 1")));
    }
}
