package com.calcula.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.calcula.expr.Expr;
import com.calcula.expr.Expr.Call;
import com.calcula.expr.Exprs;
import com.calcula.machine.Modes.Angle;

/**
 * Reading trigonometry in whatever unit the mode line says.
 *
 * <p>Every CAS works in radians, so degree mode is a rewrite of the expression rather than a setting
 * passed to the engine: {@code sin(x)} becomes {@code sin(x · π/180)}, and {@code arcsin(x)} — which
 * <em>returns</em> an angle rather than taking one — becomes {@code arcsin(x) / (π/180)}.
 *
 * <p>The factor is carried as the exact quotient {@code π/180} and never as 0.017453…, which is the
 * whole point: {@code sin(30)} in degrees becomes {@code sin(π/6)} and the engine answers {@code 1/2}.
 * Multiplying by a rounded decimal instead gives 0.49999999999999994 and quietly turns an exact
 * calculator into an approximate one.
 *
 * <p>Hyperbolic functions are deliberately absent. {@code sinh(2)} takes a plain real number, not an
 * angle, and converting it would make degree mode silently change answers that have nothing to do with
 * angles.
 */
public final class AngleConversion {

    /** Takes an angle: the argument is what needs converting. */
    private static final Set<String> TAKES_ANGLE = Set.of("Sin", "Cos", "Tan", "Cot", "Sec", "Csc");

    /** Returns an angle: the result is what needs converting. */
    private static final Set<String> RETURNS_ANGLE = Set.of("ArcSin", "ArcCos", "ArcTan", "ArcCot", "ArcSec", "ArcCsc");

    private AngleConversion() {}

    /**
     * Rewrite an expression so that a radian-based engine computes it in {@code angle}'s unit.
     *
     * <p>Radian mode returns the input untouched — identical reference, no walk — because it is both
     * the default and the overwhelmingly common case.
     */
    public static Expr toRadians(Expr e, Angle angle) {
        if (angle == Angle.RADIANS) {
            return e;
        }
        return convert(e, factorFor(angle));
    }

    /** One turn's worth of the unit, as an exact fraction of π. */
    private static Expr factorFor(Angle angle) {
        int halfTurn = angle == Angle.GRADIANS ? 200 : 180;
        return Exprs.call("Divide", Exprs.sym("Pi"), Exprs.of(halfTurn));
    }

    private static Expr convert(Expr e, Expr factor) {
        if (!(e instanceof Call c)) {
            return e;
        }
        // Children first, then this node — and the wrapper built here is never walked again, so
        // sin(sin(x)) converts each argument exactly once rather than compounding the factor.
        List<Expr> args = new ArrayList<>(c.arity());
        boolean changed = false;
        for (Expr arg : c.args()) {
            Expr converted = convert(arg, factor);
            changed |= converted != arg;
            args.add(converted);
        }
        Call rebuilt = changed ? (Call) Exprs.call(c.head(), args) : c;

        if (TAKES_ANGLE.contains(c.head()) && c.arity() == 1) {
            return Exprs.call(c.head(), Exprs.call("Times", rebuilt.arg(0), factor));
        }
        if (RETURNS_ANGLE.contains(c.head())) {
            return Exprs.call("Divide", rebuilt, factor);
        }
        return rebuilt;
    }
}
