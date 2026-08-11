package com.calcula.ui.math;

import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;

/**
 * A square root: the surd drawn as a {@link Path}, with its bar extending over the content.
 *
 * <p>Drawn rather than set from a glyph on purpose. JavaFX cannot read an OpenType MATH table, so it
 * has no access to the size variants and extensible pieces a font provides for growing a radical —
 * scaling the glyph instead would thicken the strokes with the height. A path scales exactly and costs
 * nothing.
 */
final class RadicalNode extends Region {

    private final Node content;
    private final Path surd = new Path();
    private final MathStyle style;

    RadicalNode(Node content, MathStyle style) {
        this.content = content;
        this.style = style;
        surd.getStyleClass().add("math-path");
        surd.setStrokeWidth(style.ruleThickness());
        getChildren().addAll(surd, content);
    }

    private double hookWidth() {
        return style.size() * 0.55;
    }

    /** Clearance between the bar and the top of what is under it. */
    private double overhang() {
        return style.size() * 0.16;
    }

    @Override
    protected double computePrefWidth(double height) {
        return hookWidth() + content.prefWidth(-1) + style.size() * 0.12;
    }

    @Override
    protected double computePrefHeight(double width) {
        return content.prefHeight(-1) + overhang() + style.ruleThickness();
    }

    @Override
    public double getBaselineOffset() {
        double offset = content.getBaselineOffset();
        double contentBaseline = offset == Node.BASELINE_OFFSET_SAME_AS_HEIGHT ? content.prefHeight(-1) : offset;
        return overhang() + style.ruleThickness() + contentBaseline;
    }

    /** Mathematics does not stretch: as wide as its content, wherever it is put. */
    @Override
    protected double computeMaxWidth(double height) {
        return computePrefWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        double hook = hookWidth();

        content.resizeRelocate(hook, overhang() + style.ruleThickness(), content.prefWidth(-1), content.prefHeight(-1));

        // The classic shape: a short tick, down to the foot, up to the top left, then the bar across.
        surd.getElements()
                .setAll(
                        new MoveTo(0, height * 0.62),
                        new LineTo(hook * 0.28, height * 0.55),
                        new LineTo(hook * 0.55, height * 0.98),
                        new LineTo(hook * 0.92, style.ruleThickness() / 2),
                        new LineTo(width, style.ruleThickness() / 2));
    }
}
