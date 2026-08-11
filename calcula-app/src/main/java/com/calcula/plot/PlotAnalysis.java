package com.calcula.plot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import com.calcula.parse.Formatter;

/**
 * What the algebra knows about a curve that sampling cannot find out.
 *
 * <p>Every grapher samples numerically and hopes. With a CAS in the same process the plot can be told
 * where the poles and the turning points actually <em>are</em>, which buys two things no amount of
 * sampling gives you:
 *
 * <ul>
 *   <li>The line breaks at the exact pole, rather than wherever a heuristic jump threshold happened to
 *       trip. The threshold is a guess; {@code Solve(denominator = 0)} is the answer.
 *   <li>A turning point can be labelled {@code (π/2, 1)} rather than {@code (1.5708, 1.0)}, because the
 *       root is still an exact expression when the label is written.
 * </ul>
 *
 * <p>This class is pure. It builds the queries and reads the answers; the caller owns the engine, so
 * the {@code plot} package never depends on the CAS.
 */
public record PlotAnalysis(List<Feature> asymptotes, List<Feature> criticalPoints) {

    public static final PlotAnalysis NONE = new PlotAnalysis(List.of(), List.of());

    /** A name the parser can never produce, so a root is evaluated as the constant it is. */
    private static final String NOT_A_VARIABLE = "(constant, no variable)";

    /**
     * A marked point.
     *
     * @param label the EXACT coordinates where they are known, which is the whole point of asking the
     *     algebra rather than the sampler
     */
    public record Feature(double x, double y, String label) {}

    public boolean isEmpty() {
        return asymptotes.isEmpty() && criticalPoints.isEmpty();
    }

    /** Where the x values of the asymptotes are, for the sampler to break on. */
    public double[] breakPoints() {
        double[] out = new double[asymptotes.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = asymptotes.get(i).x();
        }
        return out;
    }

    // ------------------------------------------------------------------ queries

    /**
     * Where the function blows up: the zeros of its denominator.
     *
     * <p>{@code Together} first, so a sum of fractions is combined into one and its denominator is the
     * real one. Without it, {@code 1/x + 1/(x-1)} has no denominator to speak of.
     */
    public static Expr asymptoteQuery(Expr body, String variable) {
        Expr denominator = Exprs.call("Denominator", Exprs.call("Together", body));
        return Exprs.call("Solve", Exprs.call("Equal", denominator, Exprs.ZERO), Exprs.sym(variable));
    }

    /** Where the slope is zero. */
    public static Expr criticalQuery(Expr body, String variable) {
        Expr derivative = Exprs.call("D", body, Exprs.sym(variable));
        return Exprs.call("Solve", Exprs.call("Equal", derivative, Exprs.ZERO), Exprs.sym(variable));
    }

    // ------------------------------------------------------------------ answers

    /**
     * The values out of a {@code Solve} result.
     *
     * <p>Solve answers {@code {{x -> 1}, {x -> 2}}} — a list of solution sets, each a list of rules —
     * so this is two levels of unwrapping, and it tolerates the flatter shape some heads return rather
     * than assuming one of them.
     */
    public static List<Expr> roots(Expr solveResult) {
        List<Expr> roots = new ArrayList<>();
        for (Expr solution : Exprs.items(solveResult)) {
            if (isRule(solution)) {
                roots.add(((Expr.Call) solution).arg(1));
                continue;
            }
            for (Expr rule : Exprs.items(solution)) {
                if (isRule(rule)) {
                    roots.add(((Expr.Call) rule).arg(1));
                }
            }
        }
        return roots;
    }

    private static boolean isRule(Expr e) {
        return e instanceof Expr.Call c && "Rule".equals(c.head()) && c.arity() == 2;
    }

    /**
     * Turn exact roots into marked features.
     *
     * <p>A root is kept only if it lands on the real line inside the plotted range: {@code Solve} is
     * happy to return complex roots, and a complex turning point is not somewhere to draw a dot.
     */
    public static PlotAnalysis of(
            List<Expr> asymptoteRoots, List<Expr> criticalRoots, DoubleUnaryOperator f, double xMin, double xMax) {
        List<Feature> poles = new ArrayList<>();
        for (Expr root : asymptoteRoots) {
            Double x = realValue(root);
            if (x != null && x >= xMin && x <= xMax) {
                poles.add(new Feature(x, Double.NaN, Formatter.format(root)));
            }
        }
        List<Feature> turning = new ArrayList<>();
        for (Expr root : criticalRoots) {
            Double x = realValue(root);
            if (x == null || x < xMin || x > xMax) {
                continue;
            }
            double y = f.applyAsDouble(x);
            if (!Double.isFinite(y)) {
                continue;
            }
            // The label is built from the EXACT root, so a turning point of sin reads (π/2, …) rather
            // than (1.5708, …). Losing that is losing the reason for asking the algebra at all.
            turning.add(new Feature(x, y, "(" + Formatter.format(root) + ", " + shortLabel(y) + ")"));
        }
        return new PlotAnalysis(List.copyOf(poles), List.copyOf(turning));
    }

    /**
     * A root as a real number, or null when it is not one.
     *
     * <p>Reuses {@link ExprCompiler}, which already refuses anything it cannot evaluate — including a
     * complex root, whose {@code I} is a symbol with no numeric value here. That refusal is the filter.
     */
    private static Double realValue(Expr root) {
        try {
            double value = ExprCompiler.compile(root, NOT_A_VARIABLE).applyAsDouble(0);
            return Double.isFinite(value) ? value : null;
        } catch (PlotException e) {
            return null;
        }
    }

    private static String shortLabel(double y) {
        double rounded = Math.round(y * 1000) / 1000.0;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }
}
