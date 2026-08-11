package com.calcula.cas.symja;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import org.matheclipse.core.eval.ExprEvaluator;
import org.matheclipse.core.expression.F;
import org.matheclipse.core.interfaces.IAST;
import org.matheclipse.core.interfaces.IExpr;
import org.matheclipse.core.interfaces.IFraction;
import org.matheclipse.core.interfaces.IInteger;
import org.matheclipse.core.interfaces.ISymbol;

/**
 * Converts between {@link Expr} and Symja's {@code IExpr}, in both directions, <em>totally</em>.
 *
 * <p>Totality is the whole design goal. Symja knows several thousand heads and we model none of them
 * specially; because our {@link Expr.Call} carries a {@code String} head, conversion is a rename and an
 * unknown function survives the trip untouched. An engine upgrade that adds functions cannot break this,
 * and swapping the engine later stays a real option.
 *
 * <p>Two shapes do not fit that rename directly, and each has a reserved head rather than an exception:
 *
 * <ul>
 *   <li>{@code $Apply} — an application whose HEAD is itself an expression rather than a symbol, as in
 *       Mathematica's {@code Derivative[1][f][x]}. Our head is a String, so the real head is carried as
 *       the first argument and restored on the way back.
 *   <li>{@code $Engine} — any atom we do not model (an exact complex, a string, a pattern). It carries
 *       the engine's own printed form, and converting back re-parses it <em>with the engine</em>. Lossy
 *       only in the sense that we cannot inspect it; it round-trips exactly.
 * </ul>
 *
 * <p>Both are prefixed with {@code $} because Symja will never produce a head starting with one, so a
 * genuine engine head can never be mistaken for our encoding.
 */
final class ExprAdapter {

    /** An application whose head is not a plain symbol. */
    static final String APPLY = "$Apply";

    /** An engine value we do not model, carried as its printed form. */
    static final String ENGINE = "$Engine";

    private final ExprEvaluator evaluator;

    ExprAdapter(ExprEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    // ------------------------------------------------------------------ ours → theirs

    IExpr toSymja(Expr e) {
        return switch (e) {
            case Expr.Int i -> F.ZZ(i.value());
            case Expr.Rat r -> F.QQ(F.ZZ(r.num()), F.ZZ(r.den()));
            // Via the string form, not a double: a decimal the user typed to twenty digits should not
            // silently become the nearest binary double on the way into the engine.
            case Expr.Flt f -> F.num(f.value().toString());
            case Expr.Sym s -> F.$s(s.name());
            case Expr.Call c -> fromCall(c);
        };
    }

    private IExpr fromCall(Expr.Call c) {
        if (ENGINE.equals(c.head()) && c.arity() == 1 && c.arg(0) instanceof Expr.Sym s) {
            // Hand it straight back to the engine's own parser, which produced this text.
            return evaluator.eval(s.name());
        }
        List<Expr> args = c.args();
        if (APPLY.equals(c.head()) && !args.isEmpty()) {
            IExpr head = toSymja(args.get(0));
            return F.ast(convert(args.subList(1, args.size())), head);
        }
        // Lower our display-oriented heads into the engine's canonical forms, which is exactly what
        // its own parser does. Sending a raw Divide AST instead is a measurably different code path:
        // `1.0/2` parsed by Symja is 0.5, while Divide(1.0, 2) built by hand evaluates to 1/2.
        switch (c.head()) {
            case "Divide" -> {
                if (args.size() == 2) {
                    return F.Times(toSymja(args.get(0)), F.Power(toSymja(args.get(1)), F.CN1));
                }
            }
            case "Subtract" -> {
                if (args.size() == 2) {
                    return F.Plus(toSymja(args.get(0)), F.Times(F.CN1, toSymja(args.get(1))));
                }
            }
            case "Minus" -> {
                if (args.size() == 1) {
                    return F.Times(F.CN1, toSymja(args.get(0)));
                }
            }
            default -> {
                // fall through to the generic rename
            }
        }
        return F.ast(convert(args), F.$s(c.head()));
    }

    private IExpr[] convert(List<Expr> args) {
        IExpr[] out = new IExpr[args.size()];
        for (int i = 0; i < args.size(); i++) {
            out[i] = toSymja(args.get(i));
        }
        return out;
    }

    // ------------------------------------------------------------------ theirs → ours

    Expr fromSymja(IExpr e) {
        if (e == null) {
            return Exprs.sym("Null");
        }
        // Order matters: an integer is also "real" and "a number", so test narrowest first.
        if (e.isInteger()) {
            return Exprs.of(((IInteger) e).toBigNumerator());
        }
        if (e.isFraction()) {
            IFraction f = (IFraction) e;
            return Exprs.rat(f.toBigNumerator(), f.toBigDenominator());
        }
        if (e.isSymbol()) {
            return Exprs.sym(symbolName((ISymbol) e));
        }
        if (e.isAST()) {
            IAST ast = (IAST) e;
            List<Expr> args = new ArrayList<>(ast.argSize());
            for (int i = 1; i <= ast.argSize(); i++) {
                args.add(fromSymja(ast.get(i)));
            }
            IExpr head = ast.head();
            if (head.isSymbol()) {
                Expr plain = plainInfinity(symbolName((ISymbol) head), args);
                return plain != null ? plain : Exprs.call(symbolName((ISymbol) head), args);
            }
            // A head that is itself an expression: keep it as the first argument of $Apply.
            List<Expr> applied = new ArrayList<>(args.size() + 1);
            applied.add(fromSymja(head));
            applied.addAll(args);
            return Exprs.call(APPLY, applied);
        }
        if (e.isReal()) {
            return real(e);
        }
        return engineValue(e);
    }

    /**
     * The engine's infinities, said the way a person would.
     *
     * <p>{@code 1/0} answers {@code DirectedInfinity()} and {@code log(0)} answers
     * {@code DirectedInfinity(-1)} — the engine's internal encoding of an infinity plus the direction
     * it is approached from, leaking through to the stack where nobody can read it.
     *
     * <p>Translated HERE rather than at display time, so the whole application sees the plain form:
     * the formatter, the typeset layout, the TeX and MathML writers and the clipboard all inherit it
     * without a special case each. Both forms round-trip back to the engine, {@code Infinity} and
     * {@code ComplexInfinity} being its own names for them.
     *
     * <p>The unsigned case stays {@code ComplexInfinity} rather than becoming plain infinity, because
     * they are not the same thing: {@code 1/0} has no direction, and calling it ∞ would be a tidier
     * answer that is also wrong.
     *
     * @return the plain form, or null when this is not an infinity
     */
    private static Expr plainInfinity(String head, List<Expr> args) {
        if (!"DirectedInfinity".equals(head)) {
            return null;
        }
        if (args.isEmpty()) {
            return Exprs.sym("ComplexInfinity");
        }
        if (args.size() == 1 && args.get(0) instanceof Expr.Int direction) {
            int sign = direction.value().signum();
            if (sign > 0) {
                return Exprs.sym("Infinity");
            }
            if (sign < 0) {
                return Exprs.call("Minus", Exprs.sym("Infinity"));
            }
        }
        return null; // a direction we do not model — better verbatim than wrong
    }

    /**
     * The printed name, not {@code getSymbolName()}.
     *
     * <p>Symja stores built-in symbols under a lower-case internal name — {@code Times} is {@code
     * "times"} — while {@code toString()} gives the canonical spelling. Using the internal name makes
     * every built-in head unrecognisable to the rest of the application, which shows up as output like
     * {@code times(2, x)} instead of {@code 2*x}. User symbols are unaffected either way.
     */
    private static String symbolName(ISymbol s) {
        String printed = s.toString();
        return printed.isBlank() ? s.getSymbolName() : printed;
    }

    /** Digits a double can carry. Beyond this, the printed form is the only place the value exists. */
    private static final int DOUBLE_DIGITS = 17;

    /**
     * An inexact number, at full precision.
     *
     * <p>Neither source alone is correct, which is why this is not a one-liner. For a machine {@code
     * Num}, {@code toString()} prints a DISPLAY form of about six significant figures — reading the
     * value from it silently truncated every inexact result in the application, so {@code N(Sqrt(2))}
     * came back as 1.41421 rather than 1.4142135623730951. For an {@code ApfloatNum}, the opposite
     * holds: it genuinely carries more than a double, and {@code evalf()} throws that away.
     *
     * <p>So the printed form wins only when it carries more than a double could — which is exactly the
     * arbitrary-precision case — and the double wins otherwise. Testing the precision rather than the
     * concrete class keeps this off Symja's internal type names.
     */
    private Expr real(IExpr e) {
        BigDecimal printed = null;
        try {
            printed = new BigDecimal(e.toString());
        } catch (NumberFormatException ignored) {
            // Symja can print in Mathematica's `1.0*^-5` notation, which BigDecimal will not read.
        }
        if (printed != null && printed.precision() > DOUBLE_DIGITS) {
            return Exprs.of(printed);
        }
        double value = e.evalf();
        if (Double.isFinite(value)) {
            return Exprs.of(BigDecimal.valueOf(value));
        }
        return printed != null ? Exprs.of(printed) : engineValue(e);
    }

    private Expr engineValue(IExpr e) {
        return Exprs.call(ENGINE, Exprs.sym(e.toString()));
    }
}
