package com.calcula.parse;

import com.calcula.expr.Expr;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code a .. b}, and the shape it becomes.
 *
 * <p>The shape is the load-bearing part: {@code Interval({a, b})} is the engine's own, so its
 * arithmetic — including {@code sin} over a range, which nothing here could reimplement — works
 * without a line of interval code. {@code Interval(1, 2)} would have been the obvious spelling and
 * means <em>two degenerate intervals</em> to Symja, which is wrong in a way nothing would have
 * reported.
 */
class IntervalNotationTest {

    private static String roundTrip(String source) {
        return Formatter.format(Parser.parse(source));
    }

    private static String shape(String source) {
        Expr parsed = Parser.parse(source);
        if (parsed instanceof Expr.Call c && c.arity() == 1 && c.arg(0) instanceof Expr.Call inner) {
            return c.head() + "(" + inner.head() + "/" + inner.arity() + ")";
        }
        return String.valueOf(parsed);
    }

    @Test
    void aRangeBecomesTheEnginesOwnInterval() {
        assertEquals("Interval(List/2)", shape("1 .. 2"));
    }

    @Test
    void itIsWrittenBackTheWayItWasTyped() {
        assertEquals("1 .. 2", roundTrip("1 .. 2"));
        assertEquals("-1 .. 1", roundTrip("-1 .. 1"));
        assertEquals("x .. y", roundTrip("x .. y"));
    }

    @Test
    void theDotsAreNotADecimalPoint() {
        // 1..2 without spaces has to be a range, not the number 1. followed by the number .2 — which
        // is exactly what the number scanner would have made of it.
        assertEquals("1 .. 2", roundTrip("1..2"));
        assertEquals("1.5 .. 2.5", roundTrip("1.5..2.5"));
        assertEquals("1.5", roundTrip("1.5"), "and an ordinary decimal is untouched");
    }

    @Test
    void itBindsLooserThanASum() {
        // 1 + 1 .. 4 is the range from two to four, which is how it reads.
        assertEquals("1 + 1 .. 4", roundTrip("1 + 1 .. 4"));
        assertEquals("Interval(List/2)", shape("1 + 1 .. 4"));
    }

    @Test
    void aListIsStillAList() {
        assertEquals("[1, 2]", roundTrip("[1, 2]"));
    }
}
