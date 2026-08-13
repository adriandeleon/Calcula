package com.calcula.plot;

import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

import com.calcula.expr.Expr;
import com.calcula.expr.Expr.Call;
import com.calcula.expr.Expr.Flt;
import com.calcula.expr.Expr.Int;
import com.calcula.expr.Expr.Rat;
import com.calcula.expr.Expr.Sym;

/**
 * Compiles an expression into a plain {@code double} function of one or two variables.
 *
 * <p>This exists because evaluating through the CAS is far too slow to draw with. Measured against the
 * real engine: <b>0.38 ms per point</b>, which is roughly 450 ms for a single 1200-pixel frame — fine
 * for a static answer, hopeless for dragging. A compiled closure tree evaluates in tens of nanoseconds,
 * so a plot can be resampled every frame while the viewport moves.
 *
 * <p>It handles arithmetic and the common functions and <em>refuses everything else</em> rather than
 * approximating. A symbolic constant it does not know, an unsupported head, one free variable more
 * than were asked for — all raise {@link PlotException}, and the caller reports that instead of
 * drawing a plausible wrong curve. Silently treating an unknown symbol as zero is the failure mode
 * worth avoiding here.
 *
 * <p>One compiler for both shapes rather than two, deliberately. A curve and a surface of the same
 * formula must agree about what {@code Power}, {@code Log} and every trig function mean numerically,
 * and two implementations of that are two chances to disagree — the day they do, a surface and its
 * own cross-section draw different mathematics.
 */
public final class ExprCompiler {

    private ExprCompiler() {}

    /**
     * Compile {@code e} as a function of {@code variable}.
     *
     * @throws PlotException if the expression uses anything that cannot be evaluated numerically
     */
    public static DoubleUnaryOperator compile(Expr e, String variable) {
        Node node = build(e, variable, null);
        return x -> node.valueAt(x, Double.NaN);
    }

    /**
     * Compile {@code e} as a function of two variables — a surface {@code z = f(x, y)}.
     *
     * @throws PlotException if the expression uses anything that cannot be evaluated numerically, or
     *     names a variable other than these two
     */
    public static DoubleBinaryOperator compile(Expr e, String xVariable, String yVariable) {
        if (yVariable == null || yVariable.equals(xVariable)) {
            throw new PlotException("a surface needs two different variables");
        }
        Node node = build(e, xVariable, yVariable);
        return node::valueAt;
    }

    /** Whether this expression could be drawn as a surface, without building the closure. */
    public static boolean isPlottable(Expr e, String xVariable, String yVariable) {
        try {
            compile(e, xVariable, yVariable);
            return true;
        } catch (PlotException ignored) {
            return false;
        }
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

    /**
     * A compiled fragment. An interface rather than lambdas throughout, so the tree stays readable.
     *
     * <p>Two arguments whether or not the second is used. A curve binds one name and ignores
     * {@code y}; the cost is passing a double nobody reads, which is nothing next to keeping two
     * compilers honest with each other.
     */
    @FunctionalInterface
    private interface Node {
        double valueAt(double x, double y);
    }

    private static Node build(Expr e, String xVar, String yVar) {
        return switch (e) {
            case Int i -> constant(i.value().doubleValue());
            case Flt f -> constant(f.value().doubleValue());
            case Rat r -> constant(r.num().doubleValue() / r.den().doubleValue());
            case Sym s -> symbol(s, xVar, yVar);
            case Call c -> call(c, xVar, yVar);
        };
    }

    private static Node constant(double value) {
        return (x, y) -> value;
    }

    private static Node symbol(Sym s, String xVar, String yVar) {
        if (s.name().equals(xVar)) {
            return (x, y) -> x;
        }
        if (yVar != null && s.name().equals(yVar)) {
            return (x, y) -> y;
        }
        return switch (s.name()) {
            case "Pi" -> constant(Math.PI);
            case "E" -> constant(Math.E);
            case "Degree" -> constant(Math.PI / 180);
            case "Infinity" -> constant(Double.POSITIVE_INFINITY);
            // One free name more than was asked for is not the thing being drawn. Saying so beats
            // picking one of the infinitely many answers you get by guessing a value for it — and the
            // count is what differs between a curve and a surface, not the principle.
            default -> throw new PlotException("cannot plot: " + s.name() + " has no value here");
        };
    }

    private static Node call(Call c, String xVar, String yVar) {
        List<Node> args = c.args().stream().map(a -> build(a, xVar, yVar)).toList();
        int n = args.size();
        return switch (c.head()) {
            case "Plus" ->
                (x, y) -> {
                    double sum = 0;
                    for (Node a : args) {
                        sum += a.valueAt(x, y);
                    }
                    return sum;
                };
            case "Times" ->
                (x, y) -> {
                    double product = 1;
                    for (Node a : args) {
                        product *= a.valueAt(x, y);
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
            case "Max" -> (x, y) -> reduce(args, x, y, Math::max);
            case "Min" -> (x, y) -> reduce(args, x, y, Math::min);
            case "Mod" -> binary(c, args, (a, b) -> a - b * Math.floor(a / b));
            default -> throw new PlotException("cannot plot " + c.head() + " numerically");
        };
    }

    private static double reduce(List<Node> args, double x, double y, DoubleBinaryOperator op) {
        double acc = args.get(0).valueAt(x, y);
        for (int i = 1; i < args.size(); i++) {
            acc = op.applyAsDouble(acc, args.get(i).valueAt(x, y));
        }
        return acc;
    }

    private static Node unary(Call c, List<Node> args, DoubleUnaryOperator op) {
        if (args.size() != 1) {
            throw new PlotException(c.head() + " needs one argument here, got " + args.size());
        }
        Node a = args.get(0);
        return (x, y) -> op.applyAsDouble(a.valueAt(x, y));
    }

    private static Node binary(Call c, List<Node> args, DoubleBinaryOperator op) {
        if (args.size() != 2) {
            throw new PlotException(c.head() + " needs two arguments here, got " + args.size());
        }
        Node a = args.get(0);
        Node b = args.get(1);
        return (x, y) -> op.applyAsDouble(a.valueAt(x, y), b.valueAt(x, y));
    }
}
