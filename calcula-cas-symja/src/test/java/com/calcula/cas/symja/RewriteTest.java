package com.calcula.cas.symja;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Rewrite rules against the real engine, in the notation a user types.
 *
 * <p>The claim being tested is the one the design rests on: a pattern written as {@code x_} parses to
 * {@code Pattern(x, Blank())}, which is <em>Symja's own form</em>, so it reaches the engine through
 * the same totality that carries any unrecognised head — with no change to the adapter. If that ever
 * stops being true these fail, and the alternative was translating patterns by hand in the adapter.
 */
class RewriteTest {

    private static CasEngine engine;

    @BeforeAll
    static void start() {
        engine = new SymjaEngine();
    }

    private static String rewrite(String target, String rule) throws CasException {
        return Formatter.format(
                engine.eval(com.calcula.expr.Exprs.call("ReplaceAll", Parser.parse(target), Parser.parse(rule))));
    }

    private static String rewriteRepeatedly(String target, String rule) throws CasException {
        return Formatter.format(
                engine.eval(com.calcula.expr.Exprs.call("ReplaceRepeated", Parser.parse(target), Parser.parse(rule))));
    }

    @Test
    void aPlainNameIsSubstituted() throws Exception {
        // What most people mean by a rewrite, and it needs no pattern at all.
        assertEquals("9 + y", rewrite("x^2 + y", "x -> 3"));
    }

    @Test
    void aPatternRewritesAShapeRatherThanAName() throws Exception {
        assertEquals("2 - cos(t)^2", rewrite("sin(t)^2 + 1", "sin(a_)^2 -> 1 - cos(a)^2"));
    }

    @Test
    void aBareNamePatternMatchesTheWholeThing() throws Exception {
        // Worth pinning because it surprises people: x_ matches ANY expression, and ReplaceAll tries
        // the whole formula before it tries the parts. This is correct, and it is why the plain-name
        // form above is the one to reach for when substituting a variable.
        assertEquals("3", rewrite("x^2 + y", "x_ -> 3"));
    }

    @Test
    void repeatingRunsUntilNothingChanges() throws Exception {
        assertEquals("1", rewriteRepeatedly("f(f(f(1)))", "f(u_) -> u"));
    }

    @Test
    void severalRulesAtOnce() throws Exception {
        assertEquals("3", rewrite("a + b", "[a -> 1, b -> 2]"));
    }

    @Test
    void aRuleThatMatchesNothingLeavesTheExpressionAlone() throws Exception {
        // The reason the window checks the rule's shape before asking: a rule that fails to match and
        // a thing that was never a rule are the same answer from here.
        assertEquals("x^2 + y", rewrite("x^2 + y", "z -> 3"));
    }

    @Test
    void patternsRoundTripThroughTheEngineAsPatterns() throws Exception {
        // A rule is an ordinary value: it can sit on the stack, be stored in a variable, and be saved
        // in a sheet. So it has to survive the engine unchanged rather than being evaluated into
        // something else on the way through.
        assertEquals(
                "sin(a_)^2 -> 1 - cos(a)^2", Formatter.format(engine.eval(Parser.parse("sin(a_)^2 -> 1 - cos(a)^2"))));
    }
}
