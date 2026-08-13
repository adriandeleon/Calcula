package com.calcula.expr;

import java.util.LinkedHashMap;
import java.util.Map;

import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExprsSubstituteTest {

    private static Map<String, Expr> bindings(String... pairs) {
        Map<String, Expr> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], Parser.parse(pairs[i + 1]));
        }
        return map;
    }

    private static String substituted(String source, String... pairs) {
        return Formatter.format(Exprs.substitute(Parser.parse(source), bindings(pairs)));
    }

    @Test
    void aBoundSymbolBecomesItsValue() {
        assertEquals("42", substituted("n", "n", "42"));
    }

    @Test
    void substitutionReachesEveryDepth() {
        assertEquals("sin(2*3)", substituted("sin(2 * x)", "x", "3"));
    }

    @Test
    void anUnboundSymbolIsLeftAlone() {
        // The whole of the algebra depends on this: x has to stay x for deriv(x^2, x) to mean anything.
        assertEquals("x + 1", substituted("x + 1", "n", "42"));
    }

    @Test
    void substitutionDoesNotRecurseIntoWhatItSubstituted() {
        // n -> n + 1 is a cycle, and one pass is what makes it terminate rather than a depth limit
        // chosen by guesswork. Press = again and the next layer resolves.
        assertEquals("n + 1", substituted("n", "n", "n + 1"));
    }

    @Test
    void severalNamesResolveInOnePass() {
        assertEquals("2 + 3", substituted("a + b", "a", "2", "b", "3"));
    }

    @Test
    void aTreeThatChangedNothingComesBackUntouched() {
        // Identity, not equality: the caller uses it to tell whether anything happened, and every
        // evaluation would otherwise rebuild a formula it did not change.
        Expr original = Parser.parse("sin(x) + cos(y)");
        assertSame(original, Exprs.substitute(original, bindings("n", "42")));
        assertSame(original, Exprs.substitute(original, Map.of()));
        assertSame(original, Exprs.substitute(original, null));
    }

    @Test
    void aNumberBindsToNothing() {
        assertEquals("2", substituted("2", "2", "9"));
    }
}
