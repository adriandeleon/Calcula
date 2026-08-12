package com.calcula.ui;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import com.calcula.plot.PlotValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plot is marked by what it graphs, not by where it was cropped.
 *
 * <p>Pure, so it needs no toolkit: the question is which predicate the row asks, and that is a
 * decision about expressions.
 */
class PlotExactnessTest {

    /** {@code 1/(x - 1)} — an exact formula, graphed over the default range. */
    private static Expr plotOfExactFormula() {
        Expr body = Exprs.call("Times", Exprs.of(1), Exprs.sym("x"));
        return PlotValue.of(body, "x", -10, 10);
    }

    @Test
    void aPlotOfAnExactFormulaIsNotMarkedApproximate() {
        Expr plot = plotOfExactFormula();

        // The raw predicate says yes, and that is the bug: the -10 and 10 are the viewport.
        assertTrue(
                Exprs.containsInexact(plot),
                "precondition: the range bounds really are Flt, which is why the naive predicate is wrong here");

        assertFalse(CalcWindow.markedInexact(plot), "a plot of an exact formula must not wear the amber marker");
    }

    @Test
    void aPlotOfAnApproximateFormulaIsStillMarked() {
        Expr body = Exprs.call("Times", Exprs.of(1.5), Exprs.sym("x"));
        Expr plot = PlotValue.of(body, "x", -10, 10);

        assertTrue(CalcWindow.markedInexact(plot), "the marker still has to describe the thing being graphed");
    }

    @Test
    void anOrdinaryValueIsUnaffected() {
        assertFalse(CalcWindow.markedInexact(Exprs.of(1)));
        assertTrue(CalcWindow.markedInexact(Exprs.of(1.5)));
        assertFalse(CalcWindow.markedInexact(Exprs.call("Plus", Exprs.sym("x"), Exprs.of(1))));
    }
}
