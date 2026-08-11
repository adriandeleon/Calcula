package com.calcula.plot;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;

/**
 * A plot, encoded as an ordinary {@link Expr} so it can live on the stack like any other value.
 *
 * <p>That is the point. In Emacs Calc a plot opens a separate gnuplot window and is gone; here it is
 * stack entry 1, sitting above the formula it came from, participating in the trail, and droppable with
 * the same key as a number. Formulas and figures in one column is what "the stack is a document" buys.
 *
 * <p>The head is {@code $Plot}, {@code $}-prefixed like the adapter's other internal encodings so it
 * cannot collide with an engine head. Nothing evaluates it — see {@code CalcWindow.askEngine}, which
 * hands a plot straight back rather than asking the CAS what {@code $Plot} means.
 */
public final class PlotValue {

    public static final String HEAD = "$Plot";

    private PlotValue() {}

    /**
     * @param body the expression to draw
     * @param variable the name it is a function of
     */
    public static Expr of(Expr body, String variable, double xMin, double xMax) {
        if (!(xMax > xMin)) {
            throw new PlotException("empty range: " + xMin + " to " + xMax);
        }
        return Exprs.call(HEAD, body, Exprs.sym(variable), Exprs.of(xMin), Exprs.of(xMax));
    }

    public static boolean isPlot(Expr e) {
        return e instanceof Expr.Call c && HEAD.equals(c.head()) && c.arity() == 4;
    }

    public static Expr body(Expr plot) {
        return part(plot).arg(0);
    }

    public static String variable(Expr plot) {
        return part(plot).arg(1) instanceof Expr.Sym s ? s.name() : "x";
    }

    public static double xMin(Expr plot) {
        return number(part(plot).arg(2));
    }

    public static double xMax(Expr plot) {
        return number(part(plot).arg(3));
    }

    /** Named constants, which are values rather than the thing a curve varies over. */
    private static final java.util.Set<String> CONSTANTS = java.util.Set.of("Pi", "E", "I", "Degree", "Infinity");

    /**
     * The variable a formula is a function of.
     *
     * <p>Exactly one free symbol is the answer; anything else falls back to {@code x}, which then
     * either plots (because x really is the variable) or fails with a message naming the symbol that
     * has no value. Both are better than picking one of several arbitrarily.
     */
    public static String inferVariable(Expr e) {
        java.util.LinkedHashSet<String> free = new java.util.LinkedHashSet<>();
        collectFree(e, free);
        return free.size() == 1 ? free.iterator().next() : "x";
    }

    private static void collectFree(Expr e, java.util.Set<String> into) {
        if (e instanceof Expr.Sym s) {
            if (!CONSTANTS.contains(s.name())) {
                into.add(s.name());
            }
        } else if (e instanceof Expr.Call c) {
            c.args().forEach(a -> collectFree(a, into));
        }
    }

    private static Expr.Call part(Expr plot) {
        if (!isPlot(plot)) {
            throw new PlotException("not a plot");
        }
        return (Expr.Call) plot;
    }

    private static double number(Expr e) {
        return e instanceof Expr.Num n ? Exprs.toDouble(n) : Double.NaN;
    }
}
