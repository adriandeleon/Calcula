package com.calcula.machine;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

import com.calcula.bits.Bitwise;
import com.calcula.error.ErrorForm;
import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import com.calcula.finance.Finance;
import com.calcula.modular.ModuloForm;

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
    public static Expr apply(String head, List<Expr> args, Modes modes) {
        MathContext mc = modes.mathContext();
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
                // Arithmetic ON a measurement, which is why it is here rather than in
                // applyNumeric: an error form is a Call, so the numeric fold never sees a row of
                // numbers to fold. Answers null the moment no argument is a measurement, which is
                // every ordinary sum in the calculator.
                case "Plus", "Subtract", "Times", "Divide", "Minus", "Power" -> {
                    Expr ring = inRing(head, args);
                    yield ring != null ? ring : measured(head, args, mc);
                }
                case "BitAnd" -> bits(args, 2, w -> Bitwise.and(w[0], w[1], modes.wordSize()));
                case "BitOr" -> bits(args, 2, w -> Bitwise.or(w[0], w[1], modes.wordSize()));
                case "BitXor" -> bits(args, 2, w -> Bitwise.xor(w[0], w[1], modes.wordSize()));
                case "BitNot" -> bits(args, 1, w -> Bitwise.not(w[0], modes.wordSize()));
                case "ShiftLeft" -> bits(args, 2, w -> Bitwise.shiftLeft(w[0], places(w[1]), modes.wordSize()));
                case "ShiftRight" -> bits(args, 2, w -> Bitwise.shiftRight(w[0], places(w[1]), modes.wordSize()));
                case "RotateLeft" -> bits(args, 2, w -> Bitwise.rotateLeft(w[0], places(w[1]), modes.wordSize()));
                case "RotateRight" -> bits(args, 2, w -> Bitwise.rotateRight(w[0], places(w[1]), modes.wordSize()));
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

    /**
     * One of the word operations, given exactly {@code arity} <b>whole</b> numbers.
     *
     * <p>Whole, and not merely numeric: there is no such thing as the bits of 2.5, and rounding one
     * silently to find some would be an answer nobody asked for.
     */
    private static Expr bits(List<Expr> args, int arity, java.util.function.Function<BigInteger[], BigInteger> f) {
        if (args.size() != arity) {
            return null;
        }
        BigInteger[] values = new BigInteger[arity];
        for (int at = 0; at < arity; at++) {
            if (!(args.get(at) instanceof Expr.Int whole)) {
                return null;
            }
            values[at] = whole.value();
        }
        return Exprs.of(f.apply(values));
    }

    /** A shift or rotate count, which has to fit in an int to mean anything. */
    private static int places(BigInteger count) {
        return count.intValueExact();
    }

    /**
     * Arithmetic where at least one argument is a number in a ring.
     *
     * <p>Tried before measurements, and they cannot both apply: a modulo form is whole numbers and a
     * measurement is decimals with an error, so nothing is both. An ordinary integer joins whichever
     * ring the other operand is in, which is what makes {@code (5 mod 7) + 4} mean what it looks like.
     */
    private static Expr inRing(String head, List<Expr> args) {
        if (args.stream().noneMatch(Builtins::isRingMember)) {
            return null;
        }
        BigInteger modulus = args.stream()
                .filter(Builtins::isRingMember)
                .map(e -> ((Expr.Int) ((Expr.Call) e).arg(1)).value())
                .findFirst()
                .orElse(null);
        List<ModuloForm> forms = new ArrayList<>(args.size());
        for (Expr arg : args) {
            ModuloForm form = asRingMember(arg, modulus);
            if (form == null) {
                return null;
            }
            forms.add(form);
        }
        ModuloForm result =
                switch (head) {
                    case "Plus" -> reduceRing(forms, ModuloForm::add);
                    case "Subtract" -> forms.size() == 2 ? forms.get(0).subtract(forms.get(1)) : null;
                    case "Times" -> reduceRing(forms, ModuloForm::multiply);
                    case "Divide" -> forms.size() == 2 ? forms.get(0).divide(forms.get(1)) : null;
                    case "Minus" -> forms.size() == 1 ? forms.get(0).negate() : null;
                    // Only the BASE is in the ring: an exponent is a count of multiplications, not a
                    // member of it, so 2^(3 mod 7) is not something this should quietly answer.
                    case "Power" ->
                        forms.size() == 2 && args.get(1) instanceof Expr.Int exponent
                                ? forms.get(0).power(exponent.value())
                                : null;
                    default -> null;
                };
        return result == null ? null : modulo(result);
    }

    private static ModuloForm reduceRing(List<ModuloForm> forms, java.util.function.BinaryOperator<ModuloForm> op) {
        ModuloForm acc = forms.get(0);
        for (ModuloForm next : forms.subList(1, forms.size())) {
            acc = op.apply(acc, next);
        }
        return acc;
    }

    private static boolean isRingMember(Expr e) {
        return e instanceof Expr.Call c
                && c.head().equals(MODULO)
                && c.arity() == 2
                && c.arg(0) instanceof Expr.Int
                && c.arg(1) instanceof Expr.Int;
    }

    /** A ring member, or a plain whole number joining that ring; null for anything else. */
    private static ModuloForm asRingMember(Expr e, BigInteger modulus) {
        if (isRingMember(e)) {
            Expr.Call c = (Expr.Call) e;
            return new ModuloForm(((Expr.Int) c.arg(0)).value(), ((Expr.Int) c.arg(1)).value());
        }
        return e instanceof Expr.Int whole ? new ModuloForm(whole.value(), modulus) : null;
    }

    private static Expr modulo(ModuloForm form) {
        return Exprs.call(MODULO, Exprs.of(form.value()), Exprs.of(form.modulus()));
    }

    /** The head a ring member is held as. */
    public static final String MODULO = "Modulo";

    /**
     * Arithmetic where at least one argument is a measurement.
     *
     * <p>A plain number is a measurement with no error, so mixing the two needs no separate case —
     * and a call where <em>nothing</em> is a measurement answers null immediately, leaving every
     * ordinary sum in the calculator to the path it always took.
     */
    private static Expr measured(String head, List<Expr> args, MathContext mc) {
        boolean any = args.stream().anyMatch(Builtins::isMeasurement);
        if (!any) {
            return null;
        }
        List<ErrorForm> forms = new ArrayList<>(args.size());
        for (Expr arg : args) {
            ErrorForm form = asMeasurement(arg, mc);
            if (form == null) {
                return null; // a symbol in there: leave the whole thing standing
            }
            forms.add(form);
        }
        ErrorForm result =
                switch (head) {
                    case "Plus" -> reduce(forms, (a, b) -> a.add(b, mc));
                    case "Subtract" -> forms.size() == 2 ? forms.get(0).subtract(forms.get(1), mc) : null;
                    case "Times" -> reduce(forms, (a, b) -> a.multiply(b, mc));
                    case "Divide" -> forms.size() == 2 ? forms.get(0).divide(forms.get(1), mc) : null;
                    case "Minus" -> forms.size() == 1 ? forms.get(0).negate() : null;
                    case "Power" -> power(forms, args, mc);
                    default -> null;
                };
        if (result == null) {
            return null;
        }
        // A measurement whose error has gone is a number again — x - x keeps its error, but 0 * x
        // genuinely has none, and carrying `+/- 0` around would be noise.
        return result.isExact() ? Exprs.of(result.value()) : plusMinus(result);
    }

    /** Only a whole exponent, and only on the base: the exponent itself being uncertain is another problem. */
    private static ErrorForm power(List<ErrorForm> forms, List<Expr> args, MathContext mc) {
        if (forms.size() != 2 || !(args.get(1) instanceof Expr.Int exponent)) {
            return null;
        }
        try {
            return forms.get(0).power(exponent.value().intValueExact(), mc);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private static ErrorForm reduce(List<ErrorForm> forms, java.util.function.BinaryOperator<ErrorForm> op) {
        ErrorForm acc = forms.get(0);
        for (ErrorForm next : forms.subList(1, forms.size())) {
            acc = op.apply(acc, next);
        }
        return acc;
    }

    private static boolean isMeasurement(Expr e) {
        return e instanceof Expr.Call c && c.head().equals(PLUS_MINUS) && c.arity() == 2;
    }

    /** A measurement, a plain number as one with no error, or null for anything else. */
    private static ErrorForm asMeasurement(Expr e, MathContext mc) {
        if (isMeasurement(e)) {
            Expr.Call c = (Expr.Call) e;
            BigDecimal value = decimal(c.arg(0), mc);
            BigDecimal error = decimal(c.arg(1), mc);
            return value == null || error == null ? null : new ErrorForm(value, error);
        }
        BigDecimal plain = decimal(e, mc);
        return plain == null ? null : ErrorForm.exact(plain);
    }

    private static Expr plusMinus(ErrorForm form) {
        return Exprs.call(PLUS_MINUS, Exprs.of(form.value()), Exprs.of(form.error()));
    }

    /** The head an error form is held as — a call, like everything else structured here. */
    public static final String PLUS_MINUS = "PlusMinus";

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
