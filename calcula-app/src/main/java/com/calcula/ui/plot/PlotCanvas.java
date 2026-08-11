package com.calcula.ui.plot;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

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
import javafx.scene.text.Font;

import com.calcula.plot.GraphicsScene;
import com.calcula.plot.PlotAnalysis;
import com.calcula.plot.PlotException;
import com.calcula.plot.Sampler;
import com.calcula.plot.Ticks;
import com.calcula.plot.Viewport;

/**
 * Draws a function, and lets you drag and scroll around it.
 *
 * <p>A {@link Canvas} rather than shape nodes: a curve is a couple of thousand points and is resampled
 * on every frame of a drag, which is exactly the workload a retained scene graph is worst at.
 *
 * <p>The colours are <b>styleable properties</b> rather than constants, so the plot follows the theme
 * like everything else. A canvas is invisible to CSS — nothing it paints can be selected — so the
 * properties live on this Region and the painting reads them. Hardcoding the palette would leave the
 * one part of the window that does not change with the theme.
 */
public final class PlotCanvas extends Region {

    private static final StyleablePropertyFactory<PlotCanvas> FACTORY =
            new StyleablePropertyFactory<>(Region.getClassCssMetaData());

    private static final int TARGET_TICKS = 8;
    private static final double AXIS_LABEL_SIZE = 10;

    /** Keep labels off the border, where they would be clipped into a different number. */
    private static final double LABEL_MARGIN = 12;

    private final StyleableObjectProperty<Color> curveColor =
            (StyleableObjectProperty<Color>) FACTORY.createStyleableColorProperty(
                    this, "curveColor", "-fx-curve-color", s -> s.curveColor, Color.web("#2563a8"));
    private final StyleableObjectProperty<Color> axisColor =
            (StyleableObjectProperty<Color>) FACTORY.createStyleableColorProperty(
                    this, "axisColor", "-fx-axis-color", s -> s.axisColor, Color.web("#8a8f98"));
    private final StyleableObjectProperty<Color> gridColor =
            (StyleableObjectProperty<Color>) FACTORY.createStyleableColorProperty(
                    this, "gridColor", "-fx-grid-color", s -> s.gridColor, Color.web("#2a2f37"));
    private final StyleableObjectProperty<Color> labelColor =
            (StyleableObjectProperty<Color>) FACTORY.createStyleableColorProperty(
                    this, "labelColor", "-fx-label-color", s -> s.labelColor, Color.web("#8a8f98"));

    private final Canvas canvas = new Canvas();
    private DoubleUnaryOperator function;
    private GraphicsScene scene;
    private PlotAnalysis analysis = PlotAnalysis.NONE;
    private Viewport viewport;
    private String message;

    private double dragX;
    private double dragY;

    public PlotCanvas(double width, double height) {
        getStyleClass().add("plot-canvas");
        getChildren().add(canvas);
        setPrefSize(width, height);
        setMinSize(width, height);
        viewport = Viewport.around(-10, 10, width, height);

        // Repaint when the theme resolves a different colour, or the plot silently keeps the old one.
        curveColor.addListener((o, a, b) -> draw());
        axisColor.addListener((o, a, b) -> draw());
        gridColor.addListener((o, a, b) -> draw());
        labelColor.addListener((o, a, b) -> draw());

        canvas.setOnMousePressed(this::onPress);
        canvas.setOnMouseDragged(this::onDrag);
        canvas.setOnScroll(this::onScroll);
    }

    /** Show {@code f} over the given range, choosing a y range that shows the shape. */
    public void show(DoubleUnaryOperator f, double xMin, double xMax) {
        this.function = f;
        this.scene = null;
        this.message = null;
        double w = Math.max(1, getPrefWidth());
        double h = Math.max(1, getPrefHeight());
        double[] range = Sampler.interestingRange(f, xMin, xMax, 400);
        viewport = new Viewport(xMin, xMax, range[0], range[1], w, h);
        draw();
    }

    /**
     * Show a picture the engine produced, rather than a function we sample ourselves.
     *
     * <p>The points are fixed — the engine already chose them, adaptively — so panning and zooming
     * transform them instead of resampling. That is the honest difference between this and
     * {@link #show}: exact and static against approximate and live.
     */
    public void showScene(GraphicsScene incoming) {
        this.scene = incoming;
        this.function = null;
        this.message = null;
        double w = Math.max(1, getPrefWidth());
        double h = Math.max(1, getPrefHeight());
        viewport = new Viewport(
                incoming.xRange()[0], incoming.xRange()[1], incoming.yRange()[0], incoming.yRange()[1], w, h);
        draw();
    }

    /** Poles and turning points the algebra found. */
    public void setAnalysis(PlotAnalysis found) {
        this.analysis = found == null ? PlotAnalysis.NONE : found;
        draw();
    }

    /** Show a reason instead of a curve. */
    public void showMessage(String text) {
        this.function = null;
        this.message = text;
        draw();
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
        viewport = viewport.resized(w, h);
        draw();
    }

    // ---------------------------------------------------------------- interaction

    private void onPress(MouseEvent e) {
        dragX = e.getX();
        dragY = e.getY();
    }

    private void onDrag(MouseEvent e) {
        viewport = viewport.pan(e.getX() - dragX, e.getY() - dragY);
        dragX = e.getX();
        dragY = e.getY();
        // Resampling on every drag event is only affordable because the function is compiled; through
        // the CAS this would be some hundreds of milliseconds a frame.
        draw();
    }

    private void onScroll(ScrollEvent e) {
        double factor = e.getDeltaY() > 0 ? Viewport.ZOOM_STEP : 1 / Viewport.ZOOM_STEP;
        viewport = viewport.zoomAt(factor, e.getX(), e.getY());
        draw();
    }

    // ---------------------------------------------------------------- painting

    private void draw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);

        if (message != null) {
            g.setFill(labelColor.get());
            g.setFont(Font.font(AXIS_LABEL_SIZE + 2));
            g.fillText(message, 10, h / 2);
            return;
        }
        drawGrid(g);
        drawAxes(g);
        drawScene(g);
        drawCurve(g);
        drawAnalysis(g);
    }

    /** Primitives the engine handed back. */
    private void drawScene(GraphicsContext g) {
        if (scene == null) {
            return;
        }
        g.setLineWidth(1.8);
        for (GraphicsScene.Primitive primitive : scene.primitives()) {
            g.setStroke(paint(primitive.color()));
            g.setFill(paint(primitive.color()));
            switch (primitive) {
                case GraphicsScene.Primitive.Polyline line -> strokeWorldPolyline(g, line.xs(), line.ys());
                case GraphicsScene.Primitive.Points points -> {
                    for (int i = 0; i < points.xs().length; i++) {
                        double sx = viewport.toScreenX(points.xs()[i]);
                        double sy = viewport.toScreenY(points.ys()[i]);
                        g.fillOval(sx - 2.5, sy - 2.5, 5, 5);
                    }
                }
                case GraphicsScene.Primitive.Circle circle -> {
                    double rx = circle.radius() / viewport.xSpan() * viewport.width();
                    double ry = circle.radius() / viewport.ySpan() * viewport.height();
                    double sx = viewport.toScreenX(circle.x());
                    double sy = viewport.toScreenY(circle.y());
                    g.strokeOval(sx - rx, sy - ry, 2 * rx, 2 * ry);
                }
            }
        }
    }

    private void strokeWorldPolyline(GraphicsContext g, double[] xs, double[] ys) {
        double[] sx = new double[xs.length];
        double[] sy = new double[ys.length];
        for (int i = 0; i < xs.length; i++) {
            sx[i] = viewport.toScreenX(xs[i]);
            sy[i] = viewport.toScreenY(ys[i]);
        }
        g.strokePolyline(sx, sy, xs.length);
    }

    private static javafx.scene.paint.Color paint(GraphicsScene.Rgb rgb) {
        return Color.color(clamp01(rgb.red()), clamp01(rgb.green()), clamp01(rgb.blue()));
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /**
     * The features the algebra found: poles as dashed rules, turning points as dots with their EXACT
     * coordinates.
     *
     * <p>This is the part sampling cannot produce. A numerical grapher can show you roughly where the
     * curve turns; it cannot tell you the turn is at pi/2.
     */
    private void drawAnalysis(GraphicsContext g) {
        if (analysis.isEmpty()) {
            return;
        }
        g.setFont(Font.font(AXIS_LABEL_SIZE));

        g.setStroke(axisColor.get());
        g.setLineWidth(1);
        g.setLineDashes(4, 4);
        for (PlotAnalysis.Feature pole : analysis.asymptotes()) {
            double sx = viewport.toScreenX(pole.x());
            if (sx >= 0 && sx <= canvas.getWidth()) {
                g.strokeLine(sx, 0, sx, canvas.getHeight());
            }
        }
        g.setLineDashes(null);

        g.setFill(curveColor.get());
        for (PlotAnalysis.Feature point : analysis.criticalPoints()) {
            double sx = viewport.toScreenX(point.x());
            double sy = viewport.toScreenY(point.y());
            if (sx < 0 || sx > canvas.getWidth() || sy < 0 || sy > canvas.getHeight()) {
                continue;
            }
            g.fillOval(sx - 3.5, sy - 3.5, 7, 7);
            g.setFill(labelColor.get());
            g.fillText(point.label(), sx + 6, sy - 6);
            g.setFill(curveColor.get());
        }
    }

    private void drawGrid(GraphicsContext g) {
        g.setStroke(gridColor.get());
        g.setLineWidth(1);
        g.setFill(labelColor.get());
        g.setFont(Font.font(AXIS_LABEL_SIZE));

        double w = canvas.getWidth();
        double h = canvas.getHeight();

        double xStep = Ticks.step(viewport.xSpan(), TARGET_TICKS);
        for (double x : Ticks.positions(viewport.xMin(), viewport.xMax(), TARGET_TICKS)) {
            double sx = Math.round(viewport.toScreenX(x)) + 0.5; // half-pixel: a crisp hairline
            g.strokeLine(sx, 0, sx, h);
            // Gridlines run to the edge; LABELS do not. One drawn against the border is clipped to
            // something misleading — a tick reading 400 came out as "00".
            if (sx > LABEL_MARGIN && sx < w - LABEL_MARGIN * 3) {
                g.fillText(Ticks.label(x, xStep), sx + 3, h - 4);
            }
        }
        double yStep = Ticks.step(viewport.ySpan(), TARGET_TICKS);
        for (double y : Ticks.positions(viewport.yMin(), viewport.yMax(), TARGET_TICKS)) {
            double sy = Math.round(viewport.toScreenY(y)) + 0.5;
            g.strokeLine(0, sy, w, sy);
            // Also kept clear of the bottom strip, where it would land on the x labels.
            if (sy > LABEL_MARGIN && sy < h - LABEL_MARGIN * 1.6) {
                g.fillText(Ticks.label(y, yStep), 4, sy - 3);
            }
        }
    }

    private void drawAxes(GraphicsContext g) {
        g.setStroke(axisColor.get());
        g.setLineWidth(1.4);
        if (viewport.yMin() < 0 && viewport.yMax() > 0) {
            double y = Math.round(viewport.toScreenY(0)) + 0.5;
            g.strokeLine(0, y, canvas.getWidth(), y);
        }
        if (viewport.xMin() < 0 && viewport.xMax() > 0) {
            double x = Math.round(viewport.toScreenX(0)) + 0.5;
            g.strokeLine(x, 0, x, canvas.getHeight());
        }
    }

    private void drawCurve(GraphicsContext g) {
        if (function == null) {
            return;
        }
        List<Sampler.Segment> segments;
        try {
            // Break where the algebra says the poles ARE, not only where the heuristic trips.
            segments = Sampler.sample(function, viewport, analysis.breakPoints());
        } catch (PlotException e) {
            showMessage(e.getMessage());
            return;
        }
        g.setStroke(curveColor.get());
        g.setLineWidth(1.8);
        for (Sampler.Segment segment : segments) {
            if (!segment.drawable()) {
                continue;
            }
            double[] xs = new double[segment.length()];
            double[] ys = new double[segment.length()];
            for (int i = 0; i < segment.length(); i++) {
                xs[i] = viewport.toScreenX(segment.xs()[i]);
                ys[i] = viewport.toScreenY(segment.ys()[i]);
            }
            // One polyline per continuous run: the gaps between them are the poles.
            g.strokePolyline(xs, ys, segment.length());
        }
    }

    // ---------------------------------------------------------------- css

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return FACTORY.getCssMetaData();
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return FACTORY.getCssMetaData();
    }

    /** Visible for tests. */
    public Viewport viewport() {
        return viewport;
    }
}
