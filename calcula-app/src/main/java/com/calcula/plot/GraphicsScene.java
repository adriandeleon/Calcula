package com.calcula.plot;

import java.util.ArrayList;
import java.util.List;

import com.calcula.expr.Expr;
import com.calcula.expr.Expr.Call;
import com.calcula.expr.Expr.Num;
import com.calcula.expr.Exprs;

/**
 * The picture an engine {@code Plot} returns, as data we can draw.
 *
 * <p>Symja's {@code Plot} does not hand back an image — it hands back a {@code Graphics} expression
 * holding primitives: a colour directive and a {@code Line} of points, already <em>adaptively
 * sampled</em> so the point spacing tightens where the curve bends. That sampling is better than the
 * uniform pass in {@link Sampler}, and it comes free with {@code ParametricPlot}, {@code PolarPlot} and
 * {@code ListPlot} too.
 *
 * <p>So this is the static-and-exact half of plotting, complementing the compiled-and-interactive half:
 * the points are fixed, so panning and zooming transform them rather than resampling. Both are worth
 * having and neither replaces the other.
 *
 * <p>Pure — no toolkit types. Colours are plain components so the parser stays testable without a
 * scene, and the canvas turns them into paint.
 */
public record GraphicsScene(List<Primitive> primitives, double[] xRange, double[] yRange) {

    /** A colour directive, in the 0..1 components the engine emits. */
    public record Rgb(double red, double green, double blue) {
        public static final Rgb DEFAULT = new Rgb(0.15, 0.39, 0.66);
    }

    public sealed interface Primitive {

        Rgb color();

        /** A connected run of points. */
        record Polyline(double[] xs, double[] ys, Rgb color) implements Primitive {}

        /** Unconnected marks. */
        record Points(double[] xs, double[] ys, Rgb color) implements Primitive {}

        record Circle(double x, double y, double radius, Rgb color) implements Primitive {}
    }

    public boolean isEmpty() {
        return primitives.isEmpty();
    }

    /** True for anything an engine {@code Plot} returns. */
    public static boolean isGraphics(Expr e) {
        return e instanceof Call c && "Graphics".equals(c.head()) && c.arity() >= 1;
    }

    /**
     * Read a {@code Graphics} expression.
     *
     * @throws PlotException if it is not one, or holds nothing drawable
     */
    public static GraphicsScene parse(Expr e) {
        if (!isGraphics(e)) {
            throw new PlotException("not a graphics value");
        }
        Call graphics = (Call) e;
        List<Primitive> primitives = new ArrayList<>();
        collect(graphics.arg(0), Rgb.DEFAULT, primitives);

        double[] x = null;
        double[] y = null;
        for (Expr option : graphics.args().subList(1, graphics.arity())) {
            double[][] range = plotRange(option);
            if (range != null) {
                x = range[0];
                y = range[1];
            }
        }
        if (primitives.isEmpty()) {
            throw new PlotException("the plot came back empty");
        }
        return new GraphicsScene(
                List.copyOf(primitives),
                x != null ? x : bounds(primitives, true),
                y != null ? y : bounds(primitives, false));
    }

    /**
     * Walk a primitive list, carrying the current colour.
     *
     * <p>A directive applies to everything that FOLLOWS it in the same list, which is why this threads
     * a colour along rather than looking one up per primitive.
     */
    private static void collect(Expr node, Rgb color, List<Primitive> into) {
        if (Exprs.isList(node)) {
            Rgb current = color;
            for (Expr child : Exprs.items(node)) {
                Rgb directive = rgb(child);
                if (directive != null) {
                    current = directive;
                } else {
                    collect(child, current, into);
                }
            }
            return;
        }
        if (!(node instanceof Call c)) {
            return;
        }
        switch (c.head()) {
            case "Line" -> {
                double[][] points = pointList(c);
                if (points != null) {
                    into.add(new Primitive.Polyline(points[0], points[1], color));
                }
            }
            case "Point" -> {
                double[][] points = pointList(c);
                if (points != null) {
                    into.add(new Primitive.Points(points[0], points[1], color));
                }
            }
            case "Circle", "Disk" -> {
                if (c.arity() >= 2) {
                    List<Expr> centre = Exprs.items(c.arg(0));
                    Double radius = number(c.arg(1));
                    if (centre.size() == 2 && radius != null) {
                        Double cx = number(centre.get(0));
                        Double cy = number(centre.get(1));
                        if (cx != null && cy != null) {
                            into.add(new Primitive.Circle(cx, cy, radius, color));
                        }
                    }
                }
            }
            default -> {
                // An unmodelled directive or primitive. Skipping it draws the rest rather than
                // refusing the whole picture over one thing we do not know how to paint.
            }
        }
    }

    private static Rgb rgb(Expr e) {
        if (e instanceof Call c && "RGBColor".equals(c.head()) && c.arity() >= 3) {
            Double r = number(c.arg(0));
            Double g = number(c.arg(1));
            Double b = number(c.arg(2));
            if (r != null && g != null && b != null) {
                return new Rgb(r, g, b);
            }
        }
        return null;
    }

    /** The {@code {{x,y},{x,y},…}} argument of a Line or Point, as parallel arrays. */
    private static double[][] pointList(Call c) {
        if (c.arity() < 1) {
            return null;
        }
        List<Expr> points = Exprs.items(c.arg(0));
        double[] xs = new double[points.size()];
        double[] ys = new double[points.size()];
        int n = 0;
        for (Expr point : points) {
            List<Expr> pair = Exprs.items(point);
            if (pair.size() != 2) {
                continue;
            }
            Double x = number(pair.get(0));
            Double y = number(pair.get(1));
            if (x != null && y != null && Double.isFinite(x) && Double.isFinite(y)) {
                xs[n] = x;
                ys[n] = y;
                n++;
            }
        }
        if (n < 1) {
            return null;
        }
        double[] cutX = new double[n];
        double[] cutY = new double[n];
        System.arraycopy(xs, 0, cutX, 0, n);
        System.arraycopy(ys, 0, cutY, 0, n);
        return new double[][] {cutX, cutY};
    }

    private static double[][] plotRange(Expr option) {
        if (!(option instanceof Call c) || !"Rule".equals(c.head()) || c.arity() != 2) {
            return null;
        }
        if (!Exprs.isSymbol(c.arg(0), "PlotRange")) {
            return null;
        }
        List<Expr> pair = Exprs.items(c.arg(1));
        if (pair.size() != 2) {
            return null;
        }
        double[] x = pairOf(pair.get(0));
        double[] y = pairOf(pair.get(1));
        return x != null && y != null ? new double[][] {x, y} : null;
    }

    private static double[] pairOf(Expr e) {
        List<Expr> items = Exprs.items(e);
        if (items.size() != 2) {
            return null;
        }
        Double a = number(items.get(0));
        Double b = number(items.get(1));
        return a != null && b != null && b > a ? new double[] {a, b} : null;
    }

    /** Fall back to the data's own extent when the engine gave no PlotRange. */
    private static double[] bounds(List<Primitive> primitives, boolean horizontal) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Primitive p : primitives) {
            double[] values =
                    switch (p) {
                        case Primitive.Polyline line -> horizontal ? line.xs() : line.ys();
                        case Primitive.Points points -> horizontal ? points.xs() : points.ys();
                        case Primitive.Circle circle ->
                            horizontal
                                    ? new double[] {circle.x() - circle.radius(), circle.x() + circle.radius()}
                                    : new double[] {circle.y() - circle.radius(), circle.y() + circle.radius()};
                    };
            for (double v : values) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }
        if (!(max > min)) {
            double centre = Double.isFinite(min) ? min : 0;
            return new double[] {centre - 1, centre + 1};
        }
        double margin = (max - min) * 0.05;
        return new double[] {min - margin, max + margin};
    }

    private static Double number(Expr e) {
        return e instanceof Num n ? Exprs.toDouble(n) : null;
    }
}
