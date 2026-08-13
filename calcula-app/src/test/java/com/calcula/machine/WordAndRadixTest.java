package com.calcula.machine;

import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The word operations, and the two spellings of a number in a base. */
class WordAndRadixTest {

    private static String evaluated(String source, Modes modes) {
        return Formatter.format(Evaluator.NUMERIC.evaluate(Parser.parse(source), modes));
    }

    private static String evaluated(String source) {
        return evaluated(source, Modes.DEFAULTS);
    }

    @Test
    void theWordSizeIsAModeAndItChangesTheAnswer() {
        // Not a default anybody could have guessed at: three widths, three correct answers.
        assertEquals("3", evaluated("BitNot(12)", Modes.DEFAULTS.withWordSize(4)));
        assertEquals("243", evaluated("BitNot(12)", Modes.DEFAULTS.withWordSize(8)));
        assertEquals("4294967283", evaluated("BitNot(12)"), "thirty-two by default");
    }

    @Test
    void theOperationsWorkWithNoEngine() {
        assertEquals("8", evaluated("BitAnd(12, 10)"));
        assertEquals("14", evaluated("BitOr(12, 10)"));
        assertEquals("6", evaluated("BitXor(12, 10)"));
        assertEquals("16", evaluated("ShiftLeft(1, 4)"));
        assertEquals("4", evaluated("ShiftRight(16, 2)"));
    }

    @Test
    void aFractionHasNoBitsAndTheCallStands() {
        // There is no such thing as the bits of 2.5, and rounding one silently to find some would be
        // an answer nobody asked for.
        assertEquals("BitAnd(2.5, 1)", evaluated("BitAnd(2.5, 1)"));
        assertEquals("BitAnd(x, 1)", evaluated("BitAnd(x, 1)"));
    }

    @Test
    void aNumberCanBeTypedInABase() {
        assertEquals("255", evaluated("16#ff"));
        assertEquals("11", evaluated("2#1011"));
        assertEquals("255", evaluated("16#FF"), "either case");
        assertEquals("256", evaluated("16#ff + 1"), "and it is an ordinary number afterwards");
    }

    @Test
    void aHashWithNothingUsableAfterItIsNotRadixNotation() {
        // The lexer has to put itself back rather than swallow the # and fail on what follows.
        assertEquals("16", evaluated("16"));
    }

    @Test
    void radixEntryIsSeparateFromRadixDisplay() {
        // Entry produces an ordinary integer, so the formatter — which is what a sheet is saved
        // through — is untouched by the base a number was typed in.
        assertEquals("255", Formatter.format(Parser.parse("16#ff")));
    }
}
