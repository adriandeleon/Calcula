package com.calcula.plot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;

/**
 * A surface on the stack: {@code z = f(x, y)} over a rectangle.
 *
 * <p>The sibling of {@link PlotValue}, and a separate head rather than a longer {@code $Plot},
 * because {@code isPlot} is an arity check and a value that answered yes to both would be drawn
 * twice over by two different renderers.
 *
 * <p>Like a plot, it is an ordinary stack value: it sits above the formula it came from, drops with
 * the same key as a number, and survives being saved. That last part is free — a {@code $}-headed
 * call formats and re-parses like any other, which {@link #ROUND_TRIPS} exists to keep true.
 */
public final class SurfaceValue {

    public static final String HEAD = "$Plot3D";

    /**
     * The property the file format rests on, named so it is not lost.
     *
     * <p>A sheet saves a stack value by formatting it and loads it by parsing that back, so a value
     * that prints in a form the parser cannot read is data loss at <em>save</em> time — the file looks
     * healthy and the value returns wrong. Asserted in {@code SurfaceValueTest}.
     */
    public static final String ROUND_TRIPS = "format then parse must give an equal value";

    private static final int ARITY = 7;

    private SurfaceValue() {}

    /**
     * @param body the expression drawn, in terms of the two variables
     * @param xVariable the name running along x
     * @param yVariable the name running along y
     */
    public static Expr of(
            Expr body, String xVariable, String yVariable, double xMin, double xMax, double yMin, double yMax) {
        if (!(xMax > xMin) || !(yMax > yMin)) {
            throw new PlotException("empty range: " + xMin + " to " + xMax + ", " + yMin + " to " + yMax);
        }
        if (xVariable == null || xVariable.equals(yVariable)) {
            throw new PlotException("a surface needs two different variables");
        }
        return Exprs.call(
                HEAD,
                body,
                Exprs.sym(xVariable),
                Exprs.sym(yVariable),
                Exprs.of(xMin),
                Exprs.of(xMax),
                Exprs.of(yMin),
                Exprs.of(yMax));
    }

    public static boolean isSurface(Expr e) {
        return e instanceof Expr.Call c && HEAD.equals(c.head()) && c.arity() == ARITY;
    }

    public static Expr body(Expr surface) {
        return arg(surface, 0);
    }

    public static String xVariable(Expr surface) {
        return name(arg(surface, 1));
    }

    public static String yVariable(Expr surface) {
        return name(arg(surface, 2));
    }

    public static double xMin(Expr surface) {
        return number(arg(surface, 3));
    }

    public static double xMax(Expr surface) {
        return number(arg(surface, 4));
    }

    public static double yMin(Expr surface) {
        return number(arg(surface, 5));
    }

    public static double yMax(Expr surface) {
        return number(arg(surface, 6));
    }

    /**
     * The two names an expression is a function of, in the order they are met.
     *
     * <p>Order matters and alphabetical would be the wrong rule: {@code y*sin(x)} is a function of
     * {@code y} and {@code x} in that reading order, and quietly swapping the axes on someone is a
     * confusing way to be tidy. Reading order is at least the order they wrote them in.
     *
     * @throws PlotException unless there are exactly two
     */
    public static List<String> inferVariables(Expr e) {
        Set<String> found = new LinkedHashSet<>();
        collect(e, found);
        if (found.size() != 2) {
            throw new PlotException(
                    found.size() < 2
                            ? "a surface needs two variables; this has " + found.size()
                            : "a surface needs two variables; this has " + found.size() + ": "
                                    + String.join(", ", found));
        }
        return List.copyOf(new ArrayList<>(found));
    }

    /** Free names only: a constant the compiler knows is not something to plot against. */
    private static void collect(Expr e, Set<String> into) {
        switch (e) {
            case Expr.Sym s -> {
                if (!KNOWN_CONSTANTS.contains(s.name())) {
                    into.add(s.name());
                }
            }
            case Expr.Call c -> c.args().forEach(a -> collect(a, into));
            default -> {
                // a number contributes no names
            }
        }
    }

    /** The names {@link ExprCompiler} resolves to a value rather than treating as a variable. */
    private static final Set<String> KNOWN_CONSTANTS = Set.of("Pi", "E", "Degree", "Infinity");

    private static Expr arg(Expr surface, int index) {
        if (!isSurface(surface)) {
            throw new PlotException("not a surface");
        }
        return ((Expr.Call) surface).args().get(index);
    }

    private static String name(Expr e) {
        if (e instanceof Expr.Sym s) {
            return s.name();
        }
        throw new PlotException("a surface's variable must be a name");
    }

    private static double number(Expr e) {
        if (e instanceof Expr.Num n) {
            return switch (n) {
                case Expr.Int i -> i.value().doubleValue();
                case Expr.Flt f -> f.value().doubleValue();
                case Expr.Rat r -> r.num().doubleValue() / r.den().doubleValue();
            };
        }
        throw new PlotException("a surface's range must be a number");
    }
}
