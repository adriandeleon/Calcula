package com.calcula.plot;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

import com.calcula.expr.Expr;
import com.calcula.expr.Expr.Call;
import com.calcula.expr.Expr.Flt;
import com.calcula.expr.Expr.Int;
import com.calcula.expr.Expr.Rat;
import com.calcula.expr.Expr.Sym;

/**
 * Compiles an expression into a plain {@code double} function of one variable.
 *
 * <p>This exists because evaluating through the CAS is far too slow to draw with. Measured against the
 * real engine: <b>0.38 ms per point</b>, which is roughly 450 ms for a single 1200-pixel frame — fine
 * for a static answer, hopeless for dragging. A compiled closure tree evaluates in tens of nanoseconds,
 * so a plot can be resampled every frame while the viewport moves.
 *
 * <p>It handles arithmetic and the common functions and <em>refuses everything else</em> rather than
 * approximating. A symbolic constant it does not know, an unsupported head, a second free variable —
 * all raise {@link PlotException}, and the caller reports that instead of drawing a plausible wrong
 * curve. Silently treating an unknown symbol as zero is the failure mode worth avoiding here.
 */
public final class ExprCompiler {

    private ExprCompiler() {}

    /**
     * Compile {@code e} as a function of {@code variable}.
     *
     * @throws PlotException if the expression uses anything that cannot be evaluated numerically
     */
    public static DoubleUnaryOperator compile(Expr e, String variable) {
        Node node = build(e, variable);
        return node::valueAt;
    }

    /** Whether this expression could be plotted, without building the closure. */
    public static boolean isPlottable(Expr e, String variable) {
        try {
            compile(e, variable);
            return true;
        } catch (PlotException ignored) {
            return false;
        }
    }

    /** A compiled fragment. An interface rather than lambdas throughout, so the tree stays readable. */
    @FunctionalInterface
    private interface Node {
        double valueAt(double x);
    }

    private static Node build(Expr e, String variable) {
        return switch (e) {
            case Int i -> constant(i.value().doubleValue());
            case Flt f -> constant(f.value().doubleValue());
            case Rat r -> constant(r.num().doubleValue() / r.den().doubleValue());
            case Sym s -> symbol(s, variable);
            case Call c -> call(c, variable);
        };
    }

    private static Node constant(double value) {
        return x -> value;
    }

    private static Node symbol(Sym s, String variable) {
        if (s.name().equals(variable)) {
            return x -> x;
        }
        return switch (s.name()) {
            case "Pi" -> constant(Math.PI);
            case "E" -> constant(Math.E);
            case "Degree" -> constant(Math.PI / 180);
            case "Infinity" -> constant(Double.POSITIVE_INFINITY);
            // A second free variable is not a curve. Saying so beats drawing one of the infinitely
            // many curves you get by guessing a value for it.
            default -> throw new PlotException("cannot plot: " + s.name() + " has no value here");
        };
    }

    private static Node call(Call c, String variable) {
        List<Node> args = c.args().stream().map(a -> build(a, variable)).toList();
        int n = args.size();
        return switch (c.head()) {
            case "Plus" ->
                x -> {
                    double sum = 0;
                    for (Node a : args) {
                        sum += a.valueAt(x);
                    }
                    return sum;
                };
            case "Times" ->
                x -> {
                    double product = 1;
                    for (Node a : args) {
                        product *= a.valueAt(x);
                    }
                    return product;
                };
            case "Subtract" -> binary(c, args, (a, b) -> a - b);
            case "Divide" -> binary(c, args, (a, b) -> a / b);
            case "Power" -> binary(c, args, Math::pow);
            case "Minus" -> unary(c, args, a -> -a);
            case "Sin" -> unary(c, args, Math::sin);
            case "Cos" -> unary(c, args, Math::cos);
            case "Tan" -> unary(c, args, Math::tan);
            case "ArcSin" -> unary(c, args, Math::asin);
            case "ArcCos" -> unary(c, args, Math::acos);
            case "ArcTan" -> n == 2 ? binary(c, args, Math::atan2) : unary(c, args, Math::atan);
            case "Sinh" -> unary(c, args, Math::sinh);
            case "Cosh" -> unary(c, args, Math::cosh);
            case "Tanh" -> unary(c, args, Math::tanh);
            case "Exp" -> unary(c, args, Math::exp);
            case "Log" ->
                n == 2 ? binary(c, args, (base, value) -> Math.log(value) / Math.log(base)) : unary(c, args, Math::log);
            case "Log10" -> unary(c, args, Math::log10);
            case "Log2" -> unary(c, args, a -> Math.log(a) / Math.log(2));
            case "Sqrt" -> unary(c, args, Math::sqrt);
            case "Abs" -> unary(c, args, Math::abs);
            case "Sign" -> unary(c, args, Math::signum);
            case "Floor" -> unary(c, args, Math::floor);
            case "Ceiling" -> unary(c, args, Math::ceil);
            case "Round" -> unary(c, args, a -> (double) Math.round(a));
            case "Max" -> x -> reduce(args, x, Math::max);
            case "Min" -> x -> reduce(args, x, Math::min);
            case "Mod" -> binary(c, args, (a, b) -> a - b * Math.floor(a / b));
            default -> throw new PlotException("cannot plot " + c.head() + " numerically");
        };
    }

    private static double reduce(List<Node> args, double x, java.util.function.DoubleBinaryOperator op) {
        double acc = args.get(0).valueAt(x);
        for (int i = 1; i < args.size(); i++) {
            acc = op.applyAsDouble(acc, args.get(i).valueAt(x));
        }
        return acc;
    }

    private static Node unary(Call c, List<Node> args, DoubleUnaryOperator op) {
        if (args.size() != 1) {
            throw new PlotException(c.head() + " needs one argument here, got " + args.size());
        }
        Node a = args.get(0);
        return x -> op.applyAsDouble(a.valueAt(x));
    }

    private static Node binary(Call c, List<Node> args, java.util.function.DoubleBinaryOperator op) {
        if (args.size() != 2) {
            throw new PlotException(c.head() + " needs two arguments here, got " + args.size());
        }
        Node a = args.get(0);
        Node b = args.get(1);
        return x -> op.applyAsDouble(a.valueAt(x), b.valueAt(x));
    }
}
