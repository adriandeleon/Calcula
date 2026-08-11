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
                return Exprs.call(symbolName((ISymbol) head), args);
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

    private Expr real(IExpr e) {
        try {
            return Exprs.of(new BigDecimal(e.toString()));
        } catch (NumberFormatException ex) {
            // Symja can print in Mathematica's `1.0*^-5` notation, which BigDecimal will not read.
            // Falling back to the double keeps the value rather than failing the whole conversion.
            return Exprs.of(e.evalf());
        }
    }

    private Expr engineValue(IExpr e) {
        return Exprs.call(ENGINE, Exprs.sym(e.toString()));
    }
}
