package com.calcula.ui.math;

import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.text.Text;

/**
 * Content wrapped in delimiters that grow with it.
 *
 * <p>Below a threshold the ordinary glyph is used, because a real {@code (} from the font looks better
 * than anything drawn. Above it the glyph would have to be scaled, which thickens its strokes as it
 * stretches — so a tall fence is drawn as a {@link Path} instead. That switch is the whole reason this
 * class exists: without it, parentheses around a fraction are either too short or too fat.
 */
final class FenceNode extends Region {

    /** Kinds this can draw. */
    enum Kind {
        PAREN,
        BRACKET
    }

    private final Node content;
    private final MathStyle style;
    private final Kind kind;
    private final Node left;
    private final Node right;
    private final boolean drawn;

    FenceNode(Node content, MathStyle style, Kind kind) {
        this.content = content;
        this.style = style;
        this.kind = kind;
        // A fence taller than about one line has to be drawn; a glyph scaled that far looks wrong.
        this.drawn = content.prefHeight(-1) > style.size() * 1.35;
        this.left = drawn ? new Path() : glyph(kind == Kind.PAREN ? "(" : "[");
        this.right = drawn ? new Path() : glyph(kind == Kind.PAREN ? ")" : "]");
        // A drawn fence is stroked; a glyph fence is filled. Distinct classes, or one of
        // them gets a property that outlines or blanks it.
        left.getStyleClass().add(drawn ? "math-path" : "math-text");
        right.getStyleClass().add(drawn ? "math-path" : "math-text");
        if (drawn) {
            ((Path) left).setStrokeWidth(style.ruleThickness());
            ((Path) right).setStrokeWidth(style.ruleThickness());
        }
        getChildren().addAll(left, content, right);
    }

    private Text glyph(String text) {
        Text t = new Text(text);
        t.setFont(style.upright());
        return t;
    }

    private double fenceWidth() {
        return drawn ? style.size() * 0.32 : left.prefWidth(-1);
    }

    private double contentBaseline() {
        double offset = content.getBaselineOffset();
        return offset == Node.BASELINE_OFFSET_SAME_AS_HEIGHT ? content.prefHeight(-1) : offset;
    }

    @Override
    protected double computePrefWidth(double height) {
        return 2 * fenceWidth() + content.prefWidth(-1) + style.size() * 0.08;
    }

    @Override
    protected double computePrefHeight(double width) {
        return content.prefHeight(-1);
    }

    @Override
    public double getBaselineOffset() {
        return contentBaseline();
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
        double height = getHeight();
        double fence = fenceWidth();
        double contentWidth = content.prefWidth(-1);

        if (drawn) {
            shape((Path) left, 0, height, true);
            shape((Path) right, 0, height, false);
            left.resizeRelocate(0, 0, fence, height);
            right.resizeRelocate(fence + contentWidth, 0, fence, height);
        } else {
            // A glyph is placed on the content's baseline, like any other character.
            double leftBaseline = left.getBaselineOffset();
            left.relocate(0, contentBaseline() - leftBaseline);
            right.relocate(fence + contentWidth, contentBaseline() - right.getBaselineOffset());
        }
        content.resizeRelocate(fence, 0, contentWidth, content.prefHeight(-1));
    }

    private void shape(Path path, double top, double height, boolean opening) {
        double w = fenceWidth();
        double bow = opening ? w * 0.75 : -w * 0.75;
        double x = opening ? w * 0.85 : w * 0.15;
        if (kind == Kind.BRACKET) {
            double tip = opening ? w * 0.9 : w * 0.1;
            double foot = opening ? w * 0.35 : w * 0.65;
            path.getElements()
                    .setAll(
                            new MoveTo(tip, top),
                            new LineTo(foot, top),
                            new LineTo(foot, top + height),
                            new LineTo(tip, top + height));
            return;
        }
        path.getElements()
                .setAll(
                        new MoveTo(x, top),
                        new CubicCurveTo(x - bow, top + height * 0.28, x - bow, top + height * 0.72, x, top + height));
    }
}
