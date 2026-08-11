package com.calcula.ui.math;

import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

/**
 * A built-up fraction: numerator over a rule over denominator.
 *
 * <p>The interesting part is {@link #getBaselineOffset()}. A fraction is not a line of text — its
 * baseline is nowhere near its bottom — and the whole formula lines up through that one number. The bar
 * sits on the <em>maths axis</em>, the height at which a minus sign is centred, so
 * {@code 1/2 + x} puts the bar and the plus at the same level instead of the fraction sinking to sit
 * on the text baseline.
 *
 * <p>Getting this wrong is not subtle: the fraction floats, and every enclosing row inherits the error.
 */
final class FractionNode extends Region {

    private final Node numerator;
    private final Node denominator;
    private final Rectangle rule;
    private final MathStyle style;
    private final double gap;

    FractionNode(Node numerator, Node denominator, MathStyle style) {
        this.numerator = numerator;
        this.denominator = denominator;
        this.style = style;
        this.gap = style.size() * 0.22;
        this.rule = new Rectangle();
        rule.setHeight(style.ruleThickness());
        rule.getStyleClass().add("math-rule");
        getChildren().addAll(numerator, denominator, rule);
    }

    private double partWidth() {
        return Math.max(numerator.prefWidth(-1), denominator.prefWidth(-1));
    }

    /** A little air either side, so the bar is wider than what it divides. */
    private double sideBearing() {
        return style.size() * 0.12;
    }

    @Override
    protected double computePrefWidth(double height) {
        return partWidth() + 2 * sideBearing();
    }

    @Override
    protected double computePrefHeight(double width) {
        return numerator.prefHeight(-1) + gap + style.ruleThickness() + gap + denominator.prefHeight(-1);
    }

    @Override
    public double getBaselineOffset() {
        // Distance from the top of this node down to the baseline, chosen so the rule lands on the axis.
        return numerator.prefHeight(-1) + gap + style.ruleThickness() / 2 + style.axisHeight();
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
        double numWidth = numerator.prefWidth(-1);
        double denWidth = denominator.prefWidth(-1);
        double numHeight = numerator.prefHeight(-1);

        numerator.resizeRelocate((width - numWidth) / 2, 0, numWidth, numHeight);

        double ruleY = numHeight + gap;
        rule.setWidth(width);
        rule.setX(0);
        rule.setY(ruleY);

        double denY = ruleY + style.ruleThickness() + gap;
        denominator.resizeRelocate((width - denWidth) / 2, denY, denWidth, denominator.prefHeight(-1));
    }
}
