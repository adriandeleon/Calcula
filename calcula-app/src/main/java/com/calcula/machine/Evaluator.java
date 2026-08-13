package com.calcula.machine;

import java.util.ArrayList;
import java.util.List;

import com.calcula.expr.Arith;
import com.calcula.expr.Expr;
import com.calcula.expr.Expr.Call;
import com.calcula.expr.Expr.Num;
import com.calcula.expr.Expr.Rat;
import com.calcula.expr.Exprs;

/**
 * How the machine turns an expression into an answer.
 *
 * <p>Kept as a one-method interface so the machine has no idea whether a CAS exists. Tests run it with
 * {@link #NUMERIC} alone and never start an engine; the application composes the two, trying arithmetic
 * first and falling back to the CAS.
 *
 * <p>{@link Modes} is passed in rather than held, because the modes live in the machine's state and
 * therefore change with undo. An evaluator that captured them at construction would keep computing to
 * the old precision after the mode line said otherwise — the kind of wrong answer nobody thinks to
 * check for.
 */
@FunctionalInterface
public interface Evaluator {

    Expr evaluate(Expr input, Modes modes);

    /** Leaves everything exactly as it is. Useful in tests that care about stack shape, not values. */
    Evaluator IDENTITY = (input, modes) -> input;

    /**
     * Exact arithmetic only, folding bottom-up and leaving anything else untouched.
     *
     * <p>This is the fast path, and it is worth having on its own: evaluating {@code 1/2 + 1/3} through
     * the engine measured 0.38 ms, against a gcd here. It also means the calculator still adds up when
     * no engine could be loaded.
     *
     * <p>Anything it cannot do exactly — a non-integer power, a symbol, an unknown head — it returns
     * unfolded rather than approximating, leaving the decision to whatever runs next.
     */
    Evaluator NUMERIC = (input, modes) -> foldNumeric(input, modes.mathContext());

    /**
     * Fold arithmetically first, and hand anything that did not reduce to a number on to {@code engine}.
     *
     * <p>The test is "is the result a number", not "did anything change". Those differ, and the
     * difference is a silent wrong answer: {@code (1+1)^(1/2)} folds its argument to {@code 2^(1/2)} —
     * a change — but is not an answer, and a change-based test would stop there and never ask the
     * engine for {@code Sqrt(2)}.
     *
     * <p>This is also where the modes that need an engine are applied: the angle unit is rewritten into
     * the expression on the way in, and symbolic mode decides whether to ask for a number on the way
     * out.
     */
    static Evaluator numericThen(Evaluator engine) {
        return (input, modes) -> {
            Expr folded = NUMERIC.evaluate(input, modes);
            if (folded instanceof Num) {
                return asFractionMode(folded, modes);
            }
            Expr result = engine.evaluate(AngleConversion.toRadians(folded, modes.angle()), modes);
            if (!modes.symbolic()) {
                result = numericise(result, engine, modes);
            }
            return asFractionMode(result, modes);
        };
    }

    /**
     * Ask the engine for a number, in symbolic-off mode.
     *
     * <p>If {@code N} comes back unevaluated the engine could not do it — or there is no engine at all —
     * and the request is unwrapped rather than left on the stack. Displaying {@code N(x, 12)} as an
     * answer would be worse than the exact form the user was trying to get away from.
     */
    private static Expr numericise(Expr result, Evaluator engine, Modes modes) {
        if (result instanceof Num) {
            return result;
        }
        Expr asked = Exprs.call("N", result, Exprs.of(modes.precision()));
        Expr answer = engine.evaluate(asked, modes);
        return answer instanceof Call c && "N".equals(c.head()) ? result : answer;
    }

    /**
     * Rationals become decimals when fraction mode is off.
     *
     * <p>Applied to the whole tree, not just a bare result: {@code 1/3 + x} answers
     * {@code Plus(Rat(1,3), x)}, and converting only a top-level number would leave the fraction
     * visible in exactly the answers a user turns the mode off to avoid.
     */
    private static Expr asFractionMode(Expr e, Modes modes) {
        return modes.fractions() ? e : decimalise(e, modes);
    }

    private static Expr decimalise(Expr e, Modes modes) {
        if (e instanceof Rat r) {
            return Exprs.of(Arith.toDecimal(r, modes.mathContext()));
        }
        if (!(e instanceof Call c)) {
            return e;
        }
        List<Expr> args = new ArrayList<>(c.arity());
        boolean changed = false;
        for (Expr arg : c.args()) {
            Expr converted = decimalise(arg, modes);
            changed |= converted != arg;
            args.add(converted);
        }
        return changed ? Exprs.call(c.head(), args) : e;
    }

    private static Expr foldNumeric(Expr e, java.math.MathContext mc) {
        if (!(e instanceof Call c)) {
            return e;
        }
        List<Expr> args = new ArrayList<>(c.arity());
        boolean allNumeric = true;
        for (Expr arg : c.args()) {
            Expr folded = foldNumeric(arg, mc);
            args.add(folded);
            allNumeric &= folded instanceof Num;
        }
        // Before the all-numeric gate, because one of ours takes a LIST of numbers rather than a row
        // of them, and a list is a Call. Builtins answers null for anything that is not its business,
        // so the cost to every other expression is one switch that falls through.
        Expr builtin = Builtins.apply(c.head(), args, mc);
        if (builtin != null) {
            return builtin;
        }
        if (!allNumeric) {
            return Exprs.call(c.head(), args);
        }
        try {
            Expr folded = applyNumeric(c.head(), args, mc);
            return folded == null ? Exprs.call(c.head(), args) : folded;
        } catch (ArithmeticException ex) {
            // Division by zero, 0^0, an exponent too large to compute: all real answers to give the
            // user, but not here. Leave the expression standing and let the engine or the caller
            // produce the message.
            return Exprs.call(c.head(), args);
        }
    }

    /** The fold for one head, or null when this is not arithmetic we do ourselves. */
    private static Expr applyNumeric(String head, List<Expr> args, java.math.MathContext mc) {
        List<Num> nums = new ArrayList<>(args.size());
        for (Expr arg : args) {
            nums.add((Num) arg);
        }
        return switch (head) {
            case "Plus" -> nums.isEmpty() ? Exprs.ZERO : reduce(nums, (a, b) -> Arith.add(a, b, mc));
            case "Times" -> nums.isEmpty() ? Exprs.ONE : reduce(nums, (a, b) -> Arith.mul(a, b, mc));
            case "Subtract" -> nums.size() == 2 ? Arith.sub(nums.get(0), nums.get(1), mc) : null;
            case "Divide" -> nums.size() == 2 ? Arith.div(nums.get(0), nums.get(1), mc) : null;
            case "Power" -> nums.size() == 2 ? Arith.pow(nums.get(0), nums.get(1), mc) : null;
            case "Minus" -> nums.size() == 1 ? Arith.neg(nums.get(0)) : null;
            default -> null;
        };
    }

    private static Expr reduce(List<Num> nums, java.util.function.BinaryOperator<Num> op) {
        Num acc = nums.get(0);
        for (Num n : nums.subList(1, nums.size())) {
            acc = op.apply(acc, n);
        }
        return acc;
    }
}
