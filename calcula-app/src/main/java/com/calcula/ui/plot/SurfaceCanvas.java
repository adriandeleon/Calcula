package com.calcula.ui.plot;

import java.util.ArrayList;
import java.util.List;

import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleablePropertyFactory;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import com.calcula.plot.SurfaceProjection;
import com.calcula.plot.SurfaceSampler;
import com.calcula.plot.SurfaceShading;
import com.calcula.plot.Ticks;

/**
 * A surface, drawn as a wireframe on a plain {@link Canvas}.
 *
 * <p>The sibling of {@link PlotCanvas} rather than a mode of it: almost all of that class is the
 * furniture of a two-dimensional plot — grid, axes, ticks, poles, turning points — and none of it
 * means anything here.
 *
 * <p><b>No depth buffer, and none needed.</b> A height field {@code z = f(x, y)} cannot pass through
 * itself, so sorting the cells by distance and drawing the far ones first gets the occlusion right.
 * That is what makes this an ordinary canvas: JavaFX has a real 3D scene graph, but taking it would
 * mean a second rendering technology in an all-canvas window and colours that CSS can no longer
 * reach — a {@code PhongMaterial} does not read {@code -fx-curve-color}, so the surface would stop
 * following the theme.
 *
 * <p>Sampling happens once, outside; this only ever re-projects. Turning a surface must not re-run
 * the function, which at 3,600 points would be the difference between a drag and a slideshow.
 */
public final class SurfaceCanvas extends Region {

    private static final StyleablePropertyFactory<SurfaceCanvas> FACTORY =
            new StyleablePropertyFactory<>(Region.getClassCssMetaData());

    /** A drag across the full width turns the surface most of the way round. */
    private static final double TURN_PER_PIXEL = Math.PI / 260;

    private static final double ZOOM_PER_NOTCH = 1.0015;

    /**
     * Declared here as well as on {@link PlotCanvas} because CSS metadata belongs to a class, not to
     * a hierarchy. The <em>selector</em> is shared instead — this carries the {@code plot-canvas}
     * style class, so the colours a theme already sets for a plot apply to a surface with no new rule.
     */
    private final StyleableObjectProperty<Color> curveColor =
            (StyleableObjectProperty<Color>) FACTORY.createStyleableColorProperty(
                    this, "curveColor", "-fx-curve-color", s -> s.curveColor, Color.web("#2563a8"));

    private final StyleableObjectProperty<Color> axisColor =
            (StyleableObjectProperty<Color>) FACTORY.createStyleableColorProperty(
                    this, "axisColor", "-fx-axis-color", s -> s.axisColor, Color.web("#8a8f98"));

    private final StyleableObjectProperty<Color> labelColor =
            (StyleableObjectProperty<Color>) FACTORY.createStyleableColorProperty(
                    this, "labelColor", "-fx-label-color", s -> s.labelColor, Color.web("#8a8f98"));

    /**
     * What the surface is made of, and the whole reason the far side stops showing through the near.
     *
     * <p><b>It has to be opaque.</b> Occlusion here is nothing but paint: cells go down far ones
     * first, so a near cell hides a far one only by covering it. A translucent fill puts every cell in
     * exactly the right order and still lets all of them show through — which looks so nearly right
     * that it reads as a subtlety of the renderer rather than as a colour that never resolved.
     *
     * <p>The default is white rather than transparent so a theme that fails to reach this — a typo in
     * a selector, a token that no longer exists — announces itself as a white slab on a dark ground,
     * instead of quietly restoring the see-through mesh it was added to replace.
     */
    private final StyleableObjectProperty<Color> surfaceFill =
            (StyleableObjectProperty<Color>) FACTORY.createStyleableColorProperty(
                    this, "surfaceFill", "-fx-surface-fill", s -> s.surfaceFill, Color.WHITE);

    /** How far the body of the surface is tinted from the ground toward the curve colour. */
    private static final double BODY_TINT = 0.18;

    /** How far the darkest and lightest faces move from the body colour. */
    private static final double SHADOW_MIX = 0.34;

    private static final double HIGHLIGHT_MIX = 0.20;

    /**
     * Distinct fills mixed per repaint.
     *
     * <p>A colour per cell would be thousands of short-lived objects on every frame of a drag, for a
     * difference no eye resolves. Thirty-two steps quantises the shading finely enough to look
     * continuous and bounds the mixing to a fixed cost.
     */
    private static final int SHADES = 32;

    /** Roughly how many wireframe lines to draw each way, however finely the surface is sampled. */
    private static final int WIREFRAME_LINES = 20;

    /** How far a tick mark sticks out of the axis, and where its number starts. */
    private static final double TICK_LENGTH = 4;

    private static final double TICK_GAP = 8;

    private final Canvas canvas = new Canvas();

    private SurfaceSampler.Grid grid;
    private SurfaceProjection view;
    private String message;

    private double dragX;
    private double dragY;

    public SurfaceCanvas(double width, double height) {
        getStyleClass().addAll("plot-canvas", "surface-canvas");
        getChildren().add(canvas);
        setPrefSize(width, height);
        setMinSize(width, height);

        // Repaint when the theme resolves a different colour, or the surface silently keeps the old.
        curveColor.addListener((o, a, b) -> draw());
        axisColor.addListener((o, a, b) -> draw());
        labelColor.addListener((o, a, b) -> draw());
        surfaceFill.addListener((o, a, b) -> draw());

        canvas.setOnMousePressed(this::onPress);
        canvas.setOnMouseDragged(this::onDrag);
        canvas.setOnScroll(this::onScroll);
    }

    /** Show an already-sampled surface. Sampling is the caller's job, and is done once. */
    public void show(SurfaceSampler.Grid sampled) {
        this.grid = sampled;
        this.message = sampled.isEmpty() ? "nothing to draw here" : null;
        this.view = SurfaceProjection.of(sampled, Math.max(1, getPrefWidth()), Math.max(1, getPrefHeight()));
        draw();
    }

    public void showMessage(String text) {
        this.grid = null;
        this.message = text;
        draw();
    }

    /** Visible for tests: the view the surface is currently drawn from. */
    public SurfaceProjection view() {
        return view;
    }

    /** Visible for tests: the grid being drawn. */
    SurfaceSampler.Grid grid() {
        return grid;
    }

    /** Visible for tests: the ground colour the surface is made of, as the theme resolved it. */
    Color surfaceFill() {
        return surfaceFill.get();
    }

    /** Visible for tests: the colour the wireframe is drawn in. */
    Color curveColour() {
        return curveColor.get();
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        canvas.setWidth(w);
        canvas.setHeight(h);
        canvas.relocate(0, 0);
        if (view != null) {
            view = view.resized(w, h);
        }
        draw();
    }

    // ---------------------------------------------------------------- interaction

    private void onPress(MouseEvent e) {
        dragX = e.getX();
        dragY = e.getY();
    }

    /**
     * Drag turns the surface.
     *
     * <p>A plot pans on a drag, because a curve is looked at through a window onto an endless plane.
     * A surface is an object on a table, and turning it is what a hand wants to do; panning is the
     * rarer want and can have a modifier when someone asks for it.
     */
    private void onDrag(MouseEvent e) {
        if (view == null) {
            return;
        }
        double dx = e.getX() - dragX;
        double dy = e.getY() - dragY;
        dragX = e.getX();
        dragY = e.getY();
        drag(dx, dy);
    }

    /**
     * The turn a drag of this many pixels makes.
     *
     * <p>Split out so a test drives the real thing rather than a synthetic {@code MouseEvent} — the
     * question worth asking is what a drag does to the view, not whether JavaFX delivers events.
     */
    void drag(double dx, double dy) {
        if (view == null) {
            return;
        }
        // Dragging up raises the camera, which is the direction that feels like lifting the near edge.
        view = view.turned(-dx * TURN_PER_PIXEL, dy * TURN_PER_PIXEL);
        draw();
    }

    private void onScroll(ScrollEvent e) {
        if (view == null) {
            return;
        }
        view = view.zoomedBy(Math.pow(ZOOM_PER_NOTCH, e.getDeltaY()));
        draw();
    }

    // ---------------------------------------------------------------- drawing

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        g.clearRect(0, 0, w, h);
        if (message != null) {
            g.setFill(labelColor.get());
            g.fillText(message, 12, h / 2);
            return;
        }
        if (grid == null || view == null) {
            return;
        }
        drawFloor(g);
        drawHeightAxis(g);
        drawMesh(g);
        drawHeightLabels(g);
    }

    /**
     * The cells, far ones first, each filled before it is outlined.
     *
     * <p>Sorted by the distance of the cell's middle. That is an approximation — two cells can
     * interleave in a way no single number per cell captures — but a height field is exactly the case
     * where it holds up, and the alternative is a depth buffer this does not have.
     *
     * <p><b>The sort alone does not hide anything.</b> Drawing far cells first only matters if what
     * comes later covers what came before, and an outline covers almost nothing: the correct order was
     * already here, and the surface was still transparent, because a stroked quad is a wire loop with a
     * hole in the middle. Filling is what turns the order into occlusion.
     */
    private void drawMesh(GraphicsContext g) {
        int n = grid.steps();
        List<Cell> cells = new ArrayList<>((n - 1) * (n - 1));
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1; j++) {
                // A cell with a corner the function has no value at is not drawn at all, and the hole
                // in the surface is the truth rather than a gap to be papered over.
                if (grid.quadFinite(i, j)) {
                    cells.add(new Cell(i, j, depthOf(i, j), shadeOf(i, j)));
                }
            }
        }
        cells.sort((a, b) -> Double.compare(a.depth, b.depth));

        Color[] shades = shades();
        Color curve = curveColor.get();
        int last = n - 1;
        int every = wireframeStride(n);
        g.setStroke(curve);
        g.setLineWidth(1);
        double[] xs = new double[5];
        double[] ys = new double[5];
        for (Cell cell : cells) {
            corner(cell.i, cell.j, xs, ys, 0);
            corner(cell.i + 1, cell.j, xs, ys, 1);
            corner(cell.i + 1, cell.j + 1, xs, ys, 2);
            corner(cell.i, cell.j + 1, xs, ys, 3);
            xs[4] = xs[0];
            ys[4] = ys[0];
            g.setFill(shades[cell.shade]);
            g.fillPolygon(xs, ys, 4);

            // Outlined as well as filled: the fill gives the solid, the wireframe gives the shape, and
            // a smoothly shaded surface with no lines on it reads as a blob.
            //
            // Every kept line on this cell's own boundary, not just the two leading edges. Cells are
            // painted far ones first, so a nearer neighbour's fill runs right up to — and over the
            // antialiased edge of — a line its farther neighbour already drew. Each cell redrawing all
            // four of its kept edges means whichever is painted last puts the line back.
            if (cell.j % every == 0) {
                g.strokeLine(xs[0], ys[0], xs[1], ys[1]);
            }
            if ((cell.j + 1) % every == 0 || cell.j + 1 == last) {
                g.strokeLine(xs[3], ys[3], xs[2], ys[2]);
            }
            if (cell.i % every == 0) {
                g.strokeLine(xs[0], ys[0], xs[3], ys[3]);
            }
            if ((cell.i + 1) % every == 0 || cell.i + 1 == last) {
                g.strokeLine(xs[1], ys[1], xs[2], ys[2]);
            }
        }
    }

    /**
     * Draw a line on every {@code n}th one of a grid this size.
     *
     * <p>Shading wants a fine grid — the silhouette and the light are computed per cell, and coarse
     * cells give a faceted edge and blocky lighting. A wireframe wants a coarse one: at 60 samples the
     * default surface carries 60 lines each way, and on anything that oscillates they cross into
     * moire and the picture reads as a hairball rather than as a shape. The two are separate
     * resolutions and the fill is what makes separating them possible, since the solid no longer
     * depends on the lines to exist.
     *
     * <p>Around twenty lines each way, which is roughly where every plotting package lands
     * independently — gnuplot's {@code isosamples}, matplotlib's {@code rstride}.
     */
    static int wireframeStride(int steps) {
        return Math.max(1, (int) Math.round((steps - 1) / (double) WIREFRAME_LINES));
    }

    /**
     * The fills, darkest to lightest.
     *
     * <p>Shadow and highlight are mixed toward black and white rather than scaled in brightness,
     * because scaling is relative and the ground is not: multiplying the brightness of a near-black
     * surface leaves it near-black, so on the dark theme every face would come out the same and the
     * shading would simply not appear.
     */
    private Color[] shades() {
        Color body = surfaceFill.get().interpolate(curveColor.get(), BODY_TINT);
        Color shadow = body.interpolate(Color.BLACK, SHADOW_MIX);
        Color highlight = body.interpolate(Color.WHITE, HIGHLIGHT_MIX);
        Color[] out = new Color[SHADES];
        for (int i = 0; i < SHADES; i++) {
            out[i] = shadow.interpolate(highlight, i / (double) (SHADES - 1));
        }
        return out;
    }

    /** Which of {@link #SHADES} a cell falls into, from the light its plane catches. */
    private int shadeOf(int i, int j) {
        double lit = SurfaceShading.lit(normalisedCorner(i, j), normalisedCorner(i + 1, j), normalisedCorner(i, j + 1));
        double t = (lit - SurfaceShading.AMBIENT) / (1 - SurfaceShading.AMBIENT);
        return Math.clamp((int) Math.round(t * (SHADES - 1)), 0, SHADES - 1);
    }

    private double[] normalisedCorner(int i, int j) {
        return view.normalised(grid.xs()[i], grid.ys()[j], grid.z()[i][j]);
    }

    /** The outline of the region the surface is drawn over, so the picture has a floor to sit on. */
    private void drawFloor(GraphicsContext g) {
        double z = view.zMin();
        double[] xs = new double[5];
        double[] ys = new double[5];
        put(xs, ys, 0, view.xMin(), view.yMin(), z);
        put(xs, ys, 1, view.xMax(), view.yMin(), z);
        put(xs, ys, 2, view.xMax(), view.yMax(), z);
        put(xs, ys, 3, view.xMin(), view.yMax(), z);
        xs[4] = xs[0];
        ys[4] = ys[0];
        g.setStroke(axisColor.get());
        g.setLineWidth(1);
        g.strokePolyline(xs, ys, 5);
    }

    /**
     * The upright edge the height is read against.
     *
     * <p>This is the one number a surface plot cannot otherwise show. The domain is in the expression
     * the user typed, which sits in the formula column beside the picture; the height is chosen by the
     * function, and without a scale a hill of 0.9 and a hill of 900 are the same drawing.
     */
    private void drawHeightAxis(GraphicsContext g) {
        if (view.zMax() <= view.zMin()) {
            return;
        }
        double[] at = heightAxisCorner();
        double[] foot = view.screen(at[0], at[1], view.zMin());
        double[] head = view.screen(at[0], at[1], view.zMax());
        g.setStroke(axisColor.get());
        g.setLineWidth(1);
        g.strokeLine(foot[0], foot[1], head[0], head[1]);
    }

    /**
     * Ticks and their values, drawn after the mesh so the surface never buries the scale.
     *
     * <p>The axis line itself goes down before the mesh and may be occluded by it, which is honest:
     * it is part of the box the object sits in. The numbers are not — they are the reader's, they lie
     * outside the box, and a peak swallowing them would leave the picture unmeasurable.
     */
    private void drawHeightLabels(GraphicsContext g) {
        if (view.zMax() <= view.zMin()) {
            return;
        }
        double[] at = heightAxisCorner();
        double step = Ticks.step(view.zMax() - view.zMin(), 4);

        g.setStroke(axisColor.get());
        g.setFill(labelColor.get());
        g.setLineWidth(1);
        g.setTextAlign(TextAlignment.RIGHT);
        g.setTextBaseline(VPos.CENTER);
        for (double z : Ticks.positions(view.zMin(), view.zMax(), 4)) {
            double[] p = view.screen(at[0], at[1], z);
            // Outward is simply left: the axis already stands at the leftmost corner.
            g.strokeLine(p[0], p[1], p[0] - TICK_LENGTH, p[1]);
            g.fillText(Ticks.label(z, step), p[0] - TICK_GAP, p[1]);
        }
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.BASELINE);
    }

    /**
     * The floor corner the height scale stands at: whichever projects furthest to the left.
     *
     * <p><b>Not the furthest corner, which is the obvious choice and the wrong one.</b> At the usual
     * three-quarter view the far corner lands near the middle of the picture, so on anything with real
     * height the axis rises straight through the surface and its numbers are printed over the mesh —
     * measured at x=305 of 560, in among the peaks, mid-grey on blue. Legible on a low mound, gone on
     * a tall one, which is exactly the case a scale is wanted for.
     *
     * <p>The leftmost corner is on the silhouette's edge, so the numbers sit outside the object with
     * nothing behind them. It follows the turn like the far corner did.
     */
    double[] heightAxisCorner() {
        double[][] corners = {
            {view.xMin(), view.yMin()},
            {view.xMax(), view.yMin()},
            {view.xMax(), view.yMax()},
            {view.xMin(), view.yMax()},
        };
        double[] best = corners[0];
        double leftmost = Double.POSITIVE_INFINITY;
        for (double[] c : corners) {
            double x = view.screen(c[0], c[1], view.zMin())[0];
            if (x < leftmost) {
                leftmost = x;
                best = c;
            }
        }
        return best;
    }

    private void put(double[] xs, double[] ys, int at, double x, double y, double z) {
        double[] p = view.screen(x, y, z);
        xs[at] = p[0];
        ys[at] = p[1];
    }

    private void corner(int i, int j, double[] xs, double[] ys, int at) {
        put(xs, ys, at, grid.xs()[i], grid.ys()[j], grid.z()[i][j]);
    }

    private double depthOf(int i, int j) {
        double x = (grid.xs()[i] + grid.xs()[i + 1]) / 2;
        double y = (grid.ys()[j] + grid.ys()[j + 1]) / 2;
        double z = (grid.z()[i][j] + grid.z()[i + 1][j] + grid.z()[i][j + 1] + grid.z()[i + 1][j + 1]) / 4;
        return view.depth(x, y, z);
    }

    private record Cell(int i, int j, double depth, int shade) {}

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return FACTORY.getCssMetaData();
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return FACTORY.getCssMetaData();
    }
}
