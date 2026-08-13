package com.calcula.ui.plot;

import java.util.ArrayList;
import java.util.List;

import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleablePropertyFactory;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import com.calcula.plot.SurfaceProjection;
import com.calcula.plot.SurfaceSampler;

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
        drawMesh(g);
    }

    /**
     * The cells, far ones first.
     *
     * <p>Sorted by the distance of the cell's middle. That is an approximation — two cells can
     * interleave in a way no single number per cell captures — but a height field is exactly the case
     * where it holds up, and the alternative is a depth buffer this does not have.
     */
    private void drawMesh(GraphicsContext g) {
        int n = grid.steps();
        List<Cell> cells = new ArrayList<>((n - 1) * (n - 1));
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1; j++) {
                // A cell with a corner the function has no value at is not drawn at all, and the hole
                // in the surface is the truth rather than a gap to be papered over.
                if (grid.quadFinite(i, j)) {
                    cells.add(new Cell(i, j, depthOf(i, j)));
                }
            }
        }
        cells.sort((a, b) -> Double.compare(a.depth, b.depth));

        g.setStroke(curveColor.get());
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
            g.strokePolyline(xs, ys, 5);
        }
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

    private record Cell(int i, int j, double depth) {}

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return FACTORY.getCssMetaData();
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return FACTORY.getCssMetaData();
    }
}
