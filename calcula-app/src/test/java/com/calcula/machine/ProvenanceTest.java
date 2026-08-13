package com.calcula.machine;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A value remembers what it was worked out from, and keeps remembering it while the stack is
 * rearranged around it.
 *
 * <p>The permutations are the interesting half. Nothing in {@link Machine} moves an origin
 * explicitly — the value and its origin are one entry, so a swap swaps both — and these are here to
 * say that this remains true, because the failure mode of the alternative design is a value wearing
 * somebody else's history, which looks like data rather than like a bug.
 */
class ProvenanceTest {

    /** Exact arithmetic only, so no engine is needed and what comes back is predictable. */
    private static Machine machine() {
        return new Machine(Evaluator.NUMERIC);
    }

    private static String originOf(Machine m, int position) {
        Expr origin = m.state().entryAt(position).origin();
        return origin == null ? null : Formatter.format(origin);
    }

    @Test
    void aPushedValueRemembersWhatWasTyped() {
        Machine m = machine();
        m.apply(new Op.Push(Parser.parse("1/3 + 1/6")));

        assertEquals("1/2", Formatter.format(m.state().at(1)), "the value is the answer");
        assertEquals("1/3 + 1/6", originOf(m, 1), "the origin is the question");
    }

    /** Nothing was worked out, so the origin is the value and the window will say nothing. */
    @Test
    void aPlainNumberIsItsOwnOrigin() {
        Machine m = machine();
        m.apply(new Op.Push(Exprs.of(42)));
        assertEquals("42", originOf(m, 1));
    }

    @Test
    void anAppliedOperationRemembersTheCallItMade() {
        Machine m = machine();
        m.apply(new Op.Push(Exprs.of(5)));
        m.apply(new Op.Push(Exprs.of(3)));
        m.apply(new Op.Apply("Subtract", 2));

        assertEquals("2", Formatter.format(m.state().at(1)));
        assertEquals("5 - 3", originOf(m, 1), "RPN should be able to say what it just did");
    }

    // ---- rearranging must not shuffle the histories ------------------------------------------

    @Test
    void swapMovesEachOriginWithItsValue() {
        Machine m = machine();
        m.apply(new Op.Push(Parser.parse("1/3 + 1/6")));
        m.apply(new Op.Push(Parser.parse("1/4 + 1/4")));
        m.apply(new Op.Swap());

        assertEquals("1/2", Formatter.format(m.state().at(1)));
        assertEquals("1/3 + 1/6", originOf(m, 1), "the bottom value kept its own history");
        assertEquals("1/4 + 1/4", originOf(m, 2));
    }

    @Test
    void rollMovesEachOriginWithItsValue() {
        Machine m = machine();
        m.apply(new Op.Push(Parser.parse("1 + 1")));
        m.apply(new Op.Push(Parser.parse("2 + 2")));
        m.apply(new Op.Push(Parser.parse("3 + 3")));
        m.apply(new Op.Roll(3));

        // The top went to the bottom of the group; every origin went with its value.
        assertEquals("3 + 3", originOf(m, 3));
        assertEquals("1 + 1", originOf(m, 2));
        assertEquals("2 + 2", originOf(m, 1));
    }

    @Test
    void dropTakesTheOriginWithIt() {
        Machine m = machine();
        m.apply(new Op.Push(Parser.parse("1 + 1")));
        m.apply(new Op.Push(Parser.parse("2 + 2")));
        m.apply(new Op.Drop(1));

        assertEquals(1, m.state().depth());
        assertEquals("1 + 1", originOf(m, 1));
    }

    @Test
    void dupCopiesTheOriginToo() {
        Machine m = machine();
        m.apply(new Op.Push(Parser.parse("1/3 + 1/6")));
        m.apply(new Op.Dup(1));

        assertEquals("1/3 + 1/6", originOf(m, 1));
        assertEquals("1/3 + 1/6", originOf(m, 2));
    }

    // ---- undo --------------------------------------------------------------------------------

    /**
     * Undo restores a whole state, so provenance rides along for free — which is precisely why it
     * belongs in {@link CalcState} rather than beside it in the window.
     */
    @Test
    void undoBringsBackTheHistoryAsWellAsTheValue() {
        Machine m = machine();
        m.apply(new Op.Push(Parser.parse("1/3 + 1/6")));
        m.apply(new Op.Push(Parser.parse("9 + 1")));
        m.apply(new Op.Drop(1));
        m.undo();

        assertEquals(2, m.state().depth());
        assertEquals("9 + 1", originOf(m, 1), "the dropped value came back with its history");
        assertEquals("1/3 + 1/6", originOf(m, 2));
    }

    // ---- the honest silence ------------------------------------------------------------------

    /** A state built from bare values knows nothing, and says so rather than inventing an answer. */
    @Test
    void valuesWithNoHistoryHaveNone() {
        CalcState loaded =
                CalcState.ofValues(java.util.List.of(Exprs.of(1), Exprs.of(2)), java.util.Map.of(), Modes.DEFAULTS);
        assertNull(loaded.entryAt(1).origin());
        assertNull(loaded.entryAt(2).origin());
    }

    @Test
    void theValuesAloneAreStillTheStack() {
        Machine m = machine();
        m.apply(new Op.Push(Parser.parse("1 + 1")));
        m.apply(new Op.Push(Parser.parse("2 + 2")));
        assertEquals(
                java.util.List.of("2", "4"),
                m.state().stack().stream().map(Formatter::format).toList());
        assertTrue(m.state().depth() == 2);
    }
}
