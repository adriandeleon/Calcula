package com.calcula.machine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import com.calcula.finance.Finance;

/**
 * The functions this calculator implements itself.
 *
 * <p>There is a gap between what the engine does and what a calculator is expected to do, and it is
 * not the gap anybody would guess. Symja carries a thousand symbols of algebra and answers
 * {@code TimeValue} — and returns {@code PresentValue}, {@code BitAnd} and every payment function
 * unevaluated, because they are arithmetic rather than algebra and nobody wrote them. Probed, not
 * assumed: an unrecognised head comes back <em>lower-cased</em>, so {@code pmt(...)} typed today
 * would have quietly answered {@code presentvalue(...)} and looked like a spelling mistake.
 *
 * <p>Consulted <b>before</b> the engine and inside the numeric fold, so these compose like any other
 * arithmetic — {@code pv(0.05, 10, 100) + 50} folds bottom-up exactly as {@code sqrt(4) + 50} does —
 * and so they work with no engine loaded at all, which is the same property {@link Evaluator#NUMERIC}
 * exists to give.
 *
 * <p>Returns null for anything that is not ours, which is what keeps this a layer rather than a fork
 * in the road: a head nobody here knows carries on to the engine untouched.
 */
public final class Builtins {

    private Builtins() {}

    /**
     * The value of this call, or null when it is not one of ours.
     *
     * <p>Null is also the answer when the head IS ours and the arguments are wrong — a symbolic
     * argument, or the wrong number of them. The expression then stands unevaluated, which is what
     * every other unfinished expression in this calculator does, rather than becoming an error the
     * user has to dismiss before they can finish typing.
     */
    public static Expr apply(String head, List<Expr> args, MathContext mc) {
        try {
            return switch (head) {
                case "PresentValue" -> money(args, 4, mc, d -> Finance.presentValue(d[0], d[1], d[2], d[3], mc));
                case "FutureValue" -> money(args, 4, mc, d -> Finance.futureValue(d[0], d[1], d[2], d[3], mc));
                case "Payment" -> money(args, 4, mc, d -> Finance.payment(d[0], d[1], d[2], d[3], mc));
                case "Periods" -> money(args, 4, mc, d -> Finance.periods(d[0], d[1], d[2], d[3], mc));
                case "Rate" -> money(args, 4, mc, d -> Finance.rate(d[0], d[1], d[2], d[3], mc));
                case "NetPresentValue" -> netPresentValue(args, mc);
                case "StraightLineDepreciation" -> money(args, 3, mc, d -> Finance.straightLine(d[0], d[1], d[2], mc));
                case "SumOfYearsDepreciation" ->
                    money(args, 4, mc, d -> Finance.sumOfYears(d[0], d[1], d[2], d[3], mc));
                case "DecliningBalanceDepreciation" ->
                    money(args, 5, mc, d -> Finance.decliningBalance(d[0], d[1], d[2], d[3], d[4], mc));
                default -> null;
            };
        } catch (ArithmeticException e) {
            // A rate that does not converge, a payment that never clears, a division by zero. Real
            // answers, and not ones to give from inside a fold — the expression stands and the
            // message belongs to whoever asked.
            return null;
        }
    }

    /** One of the money functions, given exactly {@code arity} numeric arguments. */
    private static Expr money(
            List<Expr> args, int arity, MathContext mc, java.util.function.Function<BigDecimal[], BigDecimal> f) {
        if (args.size() != arity) {
            return null;
        }
        BigDecimal[] values = new BigDecimal[arity];
        for (int at = 0; at < arity; at++) {
            BigDecimal value = decimal(args.get(at), mc);
            if (value == null) {
                return null;
            }
            values[at] = value;
        }
        return Exprs.of(f.apply(values));
    }

    /** {@code npv(rate, [amounts])} — the one that takes a list rather than a row of numbers. */
    private static Expr netPresentValue(List<Expr> args, MathContext mc) {
        if (args.size() != 2 || !Exprs.isList(args.get(1))) {
            return null;
        }
        BigDecimal rate = decimal(args.get(0), mc);
        if (rate == null) {
            return null;
        }
        List<BigDecimal> amounts = new ArrayList<>();
        for (Expr item : Exprs.items(args.get(1))) {
            BigDecimal amount = decimal(item, mc);
            if (amount == null) {
                return null;
            }
            amounts.add(amount);
        }
        return amounts.isEmpty() ? null : Exprs.of(Finance.netPresentValue(rate, amounts, mc));
    }

    /** A number as a decimal, or null when it is not a number at all. */
    private static BigDecimal decimal(Expr e, MathContext mc) {
        return switch (e) {
            case Expr.Int i -> new BigDecimal(i.value());
            case Expr.Flt f -> f.value();
            case Expr.Rat r -> new BigDecimal(r.num()).divide(new BigDecimal(r.den()), mc);
            default -> null;
        };
    }
}
