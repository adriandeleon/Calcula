package com.calcula.ui.math;

import java.util.List;

import javafx.scene.Node;
import javafx.scene.layout.Region;

/**
 * A horizontal run of pieces, aligned on one baseline, with atom-class spacing between them.
 *
 * <p>Written rather than using {@code HBox} with {@code Pos.BASELINE_LEFT}, which looks like it should
 * do this and does not once rows nest. An {@code HBox} does not report a baseline of its own — a
 * {@link Region} answers {@code BASELINE_OFFSET_SAME_AS_HEIGHT} unless it overrides — so an inner row
 * inside an outer one is aligned by its <em>box</em> instead of its baseline. The visible result is a
 * product like {@code 2x} inside a sum dropping below the line while the rest of the sum stays put, and
 * a function's parenthesised argument sinking away from its name.
 *
 * <p>So the baseline is computed here and reported upward, which is the same contract every other node
 * in this package honours.
 */
final class MathRow extends Region {

    private final List<Node> pieces;
    private final double[] gapBefore;

    /**
     * @param pieces the laid-out children, in order
     * @param gapBefore space to leave before each child; index 0 is unused
     */
    MathRow(List<Node> pieces, double[] gapBefore) {
        this.pieces = List.copyOf(pieces);
        this.gapBefore = gapBefore.clone();
        getChildren().addAll(this.pieces);
        getStyleClass().add("math-row");
    }

    static double baselineOf(Node node) {
        double offset = node.getBaselineOffset();
        return offset == Node.BASELINE_OFFSET_SAME_AS_HEIGHT ? node.prefHeight(-1) : offset;
    }

    @Override
    public double getBaselineOffset() {
        double baseline = 0;
        for (Node piece : pieces) {
            baseline = Math.max(baseline, baselineOf(piece));
        }
        return baseline;
    }

    @Override
    protected double computePrefWidth(double height) {
        double width = 0;
        for (int i = 0; i < pieces.size(); i++) {
            width += gapBefore[i] + pieces.get(i).prefWidth(-1);
        }
        return width;
    }

    @Override
    protected double computePrefHeight(double width) {
        double baseline = getBaselineOffset();
        double below = 0;
        for (Node piece : pieces) {
            below = Math.max(below, piece.prefHeight(-1) - baselineOf(piece));
        }
        return baseline + below;
    }

    /** Mathematics does not stretch: a row is exactly as wide as its content, wherever it is put. */
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
        double baseline = getBaselineOffset();
        double x = 0;
        for (int i = 0; i < pieces.size(); i++) {
            Node piece = pieces.get(i);
            x += gapBefore[i];
            double width = piece.prefWidth(-1);
            piece.resizeRelocate(x, baseline - baselineOf(piece), width, piece.prefHeight(-1));
            x += width;
        }
    }
}
