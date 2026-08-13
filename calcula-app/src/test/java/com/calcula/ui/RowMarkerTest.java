package com.calcula.ui;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import com.calcula.plot.PlotValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the gutter says, and what it refuses to guess.
 *
 * <p>Pure, so it needs no toolkit: every question here is about expressions.
 */
class RowMarkerTest {

    private static Expr held(String name, Expr... args) {
        return Exprs.call("Hold", Exprs.call(name, args));
    }

    // ---- exactness ------------------------------------------------------------------------

    @Test
    void aPlotOfAnExactFormulaIsNotMarked() {
        Expr body = Exprs.call("Times", Exprs.of(1), Exprs.sym("x"));
        Expr plot = PlotValue.of(body, "x", -10, 10);

        // The raw predicate says yes, and that is exactly the trap: the -10 and 10 are the viewport.
        assertTrue(
                Exprs.containsInexact(plot),
                "precondition: the range bounds really are Flt, which is why the naive predicate is wrong here");
        assertFalse(RowMarker.unsettled(plot), "a plot of an exact formula must not wear the marker");
    }

    @Test
    void aPlotOfAnApproximateFormulaIsStillMarked() {
        Expr body = Exprs.call("Times", Exprs.of(1.5), Exprs.sym("x"));
        assertTrue(
                RowMarker.unsettled(PlotValue.of(body, "x", -10, 10)),
                "the marker still has to describe the thing being graphed");
    }

    @Test
    void ordinaryValuesAreUnaffected() {
        assertFalse(RowMarker.unsettled(Exprs.of(1)));
        assertTrue(RowMarker.unsettled(Exprs.of(1.5)));
        assertFalse(RowMarker.unsettled(Exprs.call("Plus", Exprs.sym("x"), Exprs.of(1))));
    }

    // ---- not finished ---------------------------------------------------------------------

    @Test
    void aHeldCallIsMarkedAndNamesWhatWasNotEvaluated() {
        Expr value = held("Fibonacci", Exprs.of(100));

        assertTrue(RowMarker.unsettled(value), "a held call is not a final answer");
        assertEquals("Fibonacci", RowMarker.heldName(value));
        assertNotNull(RowMarker.explanation(value));
        assertTrue(RowMarker.explanation(value).contains("Fibonacci"), "the explanation should name the head");
    }

    /**
     * The limit, stated as a test so it is a decision rather than an oversight.
     *
     * <p>An unrecognised head is passed through untouched — the real engine turns
     * {@code Frobnicate(3)} into {@code frobnicate(3)} — which is structurally identical to a
     * perfectly good symbolic result. Marking on "did not reduce to a number" would paint the marker
     * on most of what a CAS correctly returns.
     */
    @Test
    void anUnrecognisedHeadIsNotDetectable() {
        Expr passedThrough = Exprs.call("frobnicate", Exprs.of(3));
        assertNull(RowMarker.heldName(passedThrough));
        assertFalse(
                RowMarker.unsettled(passedThrough),
                "indistinguishable from a symbolic result, so it must not be guessed at");
    }

    @Test
    void anExactValueHasNothingToExplain() {
        assertNull(RowMarker.explanation(Exprs.of(1)));
    }

    @Test
    void anApproximateValueSaysWhyItIsMarked() {
        String why = RowMarker.explanation(Exprs.of(1.5));
        assertNotNull(why);
        assertTrue(why.toLowerCase().contains("approximate"));
    }

    // ---- read aloud -----------------------------------------------------------------------

    @Test
    void aRowIsSpokenAsItsPositionAndItsValue() {
        String said = RowMarker.spoken(3, Exprs.call("Plus", Exprs.sym("x"), Exprs.of(1)), null);
        assertTrue(said.startsWith("3: "), "the level is the first thing a reader needs");
        assertTrue(said.contains("x"), "and then the value itself");
    }

    @Test
    void aMarkedRowAlsoSpeaksItsReason() {
        String said = RowMarker.spoken(1, held("Fibonacci", Exprs.of(100)), null);
        assertTrue(said.contains("Fibonacci"));
        assertFalse(said.contains("\n"), "a screen reader wants one line, not a paragraph");
    }

    // ---- where it came from ----------------------------------------------------------------

    @Test
    void aValueThatIsItsOwnOriginSaysNothing() {
        Expr fortyTwo = Exprs.of(42);
        assertNull(RowMarker.origin(fortyTwo, fortyTwo), "\"from: 42\" beside 42 is noise");
    }

    @Test
    void aValueWithNoKnownOriginSaysNothing() {
        assertNull(RowMarker.origin(Exprs.of(42), null), "a sheet loaded from a file knows nothing");
    }

    /** The case the whole thing is for: the value and what produced it look nothing alike. */
    @Test
    void aValueWorkedOutFromSomethingElseSaysWhat() {
        Expr result = Exprs.list(Exprs.of(3), Exprs.of(5));
        Expr asked = Exprs.call("FactorInteger", Exprs.of(15));
        String said = RowMarker.origin(result, asked);
        assertNotNull(said);
        assertTrue(said.contains("FactorInteger"), said);
    }

    @Test
    void aSpokenRowMentionsWhereItCameFrom() {
        String said =
                RowMarker.spoken(1, Exprs.list(Exprs.of(3), Exprs.of(5)), Exprs.call("FactorInteger", Exprs.of(15)));
        assertTrue(said.startsWith("1: "));
        assertTrue(said.contains("FactorInteger"), said);
    }
}
