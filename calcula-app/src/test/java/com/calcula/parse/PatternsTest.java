package com.calcula.parse;

import com.calcula.expr.Expr;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PatternsTest {

    private static String roundTrip(String source) {
        return Formatter.format(Parser.parse(source));
    }

    private static String describe(String source) {
        Expr parsed = Parser.parse(source);
        return parsed instanceof Expr.Call c ? c.head() + "/" + c.arity() : String.valueOf(parsed);
    }

    @Test
    void patternsParseToTheEnginesOwnShape() {
        // Pattern(name, Blank()) is not a translation — it is what Symja itself uses, which is why a
        // pattern reaches the engine through the same totality that carries any unknown head.
        assertEquals("Pattern/2", describe("x_"));
        assertEquals("Blank/0", describe("_"));
        assertEquals("BlankSequence/0", describe("__"));
        assertEquals("Pattern/2", describe("args__"));
    }

    @Test
    void aPatternIsWrittenBackTheWayItWasTyped() {
        assertEquals("x_", roundTrip("x_"));
        assertEquals("_", roundTrip("_"));
        assertEquals("__", roundTrip("__"));
        assertEquals("x__", roundTrip("x__"));
        assertEquals("x___", roundTrip("x___"));
    }

    @Test
    void aPatternSurvivesInsideAFormula() {
        // The round trip is what .calc rests on, so this is the case that matters: a rule stored in a
        // sheet has to come back as the rule that was saved.
        assertEquals("sin(a_)^2 -> 1 - cos(a)^2", roundTrip("sin(a_)^2 -> 1 - cos(a)^2"));
        assertEquals("f(x_, y_) -> x + y", roundTrip("f(x_, y_) -> x + y"));
    }

    @Test
    void anUnderscoreInTheMiddleOfANameIsStillJustAName() {
        // The rule is about trailing underscores only. my_var has been a variable all along and stays
        // one, which is the whole reason this could be done without touching the lexer.
        assertEquals("my_var", roundTrip("my_var"));
        assertNull(Patterns.forName("my_var"));
    }

    @Test
    void fourUnderscoresIsNotNotationAnybodyMeans() {
        assertNull(Patterns.forName("x____"));
        assertEquals("x____", roundTrip("x____"), "so it stays an ordinary name");
    }

    @Test
    void anOrdinaryNameIsNotAPattern() {
        assertNull(Patterns.forName("x"));
        assertNull(Patterns.forName(null));
    }
}
