package com.calcula.ui;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import com.calcula.parse.Formatter;
import com.calcula.plot.PlotValue;
import com.calcula.plot.SurfaceValue;

/**
 * What a stack row's gutter says about the value beside it, and why.
 *
 * <p>Pure and separate from the cell that draws it: these are decisions about expressions, and the
 * cell should be asking a question rather than working one out. Every answer here is also a sentence
 * the row can show, because a marker nobody can interrogate is a marker that gets ignored.
 *
 * <p>The rail carries one meaning with two halves — <b>not exact, or not finished</b>. Amber for a
 * value that has floating-point error in it, and amber for a value the engine declined to work out.
 * Both are "do not read this as a final answer", which is the only thing the colour is claiming.
 */
public final class RowMarker {

    /**
     * The head an engine wraps something in when it will not evaluate it.
     *
     * <p>Verified against the real engine rather than assumed: {@code Fibonacci(100)} comes back as
     * {@code Hold(Fibonacci(100))}.
     */
    private static final String HOLD = "Hold";

    private RowMarker() {}

    /**
     * Whether the row wears the amber rail.
     *
     * <p>Deliberately the union of the two halves, so there is one marker and not two competing
     * colours for "this is not a final answer".
     */
    public static boolean unsettled(Expr value) {
        return inexact(value) || heldName(value) != null || undrawnGraphics(value);
    }

    /**
     * Carries floating-point error.
     *
     * <p>{@link Exprs#containsInexact} everywhere except a plot, where it answers the wrong question.
     * {@code PlotValue.of} carries the viewport as two doubles — the default range is
     * {@code -10, 10} — so a plot is a {@code Call} with two {@code Flt} arguments, and the
     * predicate, which walks every argument, marked <b>every plot ever drawn</b> approximate. The
     * bounds say where the picture was cropped, not that the function is inexact, so what gets marked
     * is the thing being graphed.
     */
    public static boolean inexact(Expr value) {
        // A picture is judged by what it draws, never by the numbers describing where it was cropped.
        // Those bounds are doubles, so asking the raw predicate marks every plot ever drawn — and a
        // surface has four of them rather than two, so forgetting it here is the same bug twice.
        if (PlotValue.isPlot(value)) {
            return Exprs.containsInexact(PlotValue.body(value));
        }
        if (SurfaceValue.isSurface(value)) {
            return Exprs.containsInexact(SurfaceValue.body(value));
        }
        return Exprs.containsInexact(value);
    }

    /**
     * The name the engine declined to evaluate, or null.
     *
     * <p>Only {@code Hold} is detected, and that is a real limit rather than an oversight. There are
     * two shapes of "nothing happened" and the engine only distinguishes one of them: it wraps
     * {@code Fibonacci(100)} in {@code Hold}, but an unrecognised head is passed through untouched —
     * {@code Frobnicate(3)} comes back as {@code frobnicate(3)}, which is structurally
     * indistinguishable from a perfectly good symbolic result like {@code 1 + x}. Marking on "did not
     * reduce to a number" would paint the marker on most of what a CAS correctly returns, which is
     * the same mistake as using {@code !isExact}.
     */
    public static String heldName(Expr value) {
        if (!(value instanceof Expr.Call call) || !HOLD.equals(call.head()) || call.arity() == 0) {
            return null;
        }
        Expr inner = call.args().get(0);
        return inner instanceof Expr.Call held ? held.head() : Formatter.format(inner);
    }

    /**
     * What the rail would say if asked, or null when it has nothing to say.
     *
     * <p>The held case names the discovery surface rather than just reporting the failure, because
     * the question it raises — is that the right spelling, does this engine have it at all — already
     * has an answer one chord away.
     */
    public static String explanation(Expr value) {
        String held = heldName(value);
        if (held != null) {
            return held + " was not evaluated: the engine handed it back unchanged.\n"
                    + "Check the name and its arguments — C-h f lists everything callable.";
        }
        if (undrawnGraphics(value)) {
            return "This is a picture the engine made in three dimensions, and nothing here draws one.\n"
                    + "Put the formula on the stack and use Surface instead.";
        }
        if (inexact(value)) {
            return "Approximate: this value carries a floating-point number, so it is not exact.";
        }
        return null;
    }

    /**
     * Where a value came from, as a sentence, or null when nothing knows or it would say nothing.
     *
     * <p>Silent when the origin is the value itself, which is most of the time: typing {@code 42}
     * produces {@code 42}, and "from: 42" beside 42 is noise. What is worth saying is the case where
     * the two differ — {@code [3, 5, 17, …]} came from {@code FactorInteger(2^64 - 1)}, and nothing
     * on the row said so.
     */
    public static String origin(Expr value, Expr origin) {
        if (origin == null || origin.equals(value)) {
            return null;
        }
        return "from: " + Formatter.format(origin);
    }

    /**
     * A picture the engine made that this window cannot draw.
     *
     * <p>{@code Plot3D(...)} typed at the engine comes back as {@code Graphics3D}, and the scene
     * reader accepts {@code Graphics} only — so it landed typeset, looking like a call that had not
     * been evaluated. That is the silent no-op the rail exists to catch, arriving through a door
     * nobody was watching.
     */
    static boolean undrawnGraphics(Expr value) {
        return value instanceof Expr.Call c && "Graphics3D".equals(c.head());
    }

    /**
     * The row read aloud, for assistive technology.
     *
     * <p>Set mathematics is a tree of {@code Text} nodes with no text of its own, so a screen reader
     * finds a bag of glyphs where a formula is. {@code Formatter} is already the parser's inverse and
     * already tested as such, which makes it the one description guaranteed to say what the value
     * actually is.
     */
    public static String spoken(int position, Expr value, Expr origin) {
        StringBuilder said = new StringBuilder(position + ": " + Formatter.format(value));
        String from = origin(value, origin);
        if (from != null) {
            said.append(", ").append(from);
        }
        String why = explanation(value);
        if (why != null) {
            said.append(". ").append(why.replace('\n', ' '));
        }
        return said.toString();
    }
}
