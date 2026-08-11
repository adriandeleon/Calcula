package com.calcula.ui.math;

import javafx.scene.Node;
import javafx.scene.layout.Region;

/**
 * A base with a raised superscript: {@code x²}, {@code e^(i·pi)}.
 *
 * <p>The script is positioned by its own baseline, raised a fixed fraction of the base size — not by
 * its top or its centre. Aligning by anything else makes {@code x^2} and {@code x^y} sit at different
 * heights, because the glyphs have different extents.
 *
 * <p>When the raised script reaches above the base, this node grows upward and its baseline moves down
 * accordingly, so an enclosing row still lines everything up on one baseline.
 */
final class ScriptNode extends Region {

    private final Node base;
    private final Node script;
    private final double shift;

    ScriptNode(Node base, Node script, MathStyle style) {
        this.base = base;
        this.script = script;
        // How far the script's baseline rises above the base's. TeX varies this by style; a fixed
        // fraction of the base size is close enough without a MATH table to consult.
        this.shift = style.size() * 0.45;
        getChildren().addAll(base, script);
    }

    private static double baselineOf(Node node) {
        double offset = node.getBaselineOffset();
        // Regions that do not know better report BASELINE_OFFSET_SAME_AS_HEIGHT, meaning "my bottom".
        return offset == Node.BASELINE_OFFSET_SAME_AS_HEIGHT ? node.prefHeight(-1) : offset;
    }

    private double kern() {
        return 1;
    }

    @Override
    public double getBaselineOffset() {
        return Math.max(baselineOf(base), shift + baselineOf(script));
    }

    @Override
    protected double computePrefWidth(double height) {
        return base.prefWidth(-1) + kern() + script.prefWidth(-1);
    }

    @Override
    protected double computePrefHeight(double width) {
        double baseline = getBaselineOffset();
        double belowBase = base.prefHeight(-1) - baselineOf(base);
        double belowScript = script.prefHeight(-1) - baselineOf(script) - shift;
        return baseline + Math.max(belowBase, belowScript);
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
        double baseline = getBaselineOffset();
        double baseWidth = base.prefWidth(-1);

        base.resizeRelocate(0, baseline - baselineOf(base), baseWidth, base.prefHeight(-1));
        script.resizeRelocate(
                baseWidth + kern(), baseline - shift - baselineOf(script), script.prefWidth(-1), script.prefHeight(-1));
    }
}
