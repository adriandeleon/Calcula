package com.calcula.ui.math;

import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;

/**
 * Size and style context for one piece of a formula — TeX's notion of "style", reduced to what a
 * renderer without an OpenType MATH table can actually use.
 *
 * <p>TeX has four styles (display, text, script, scriptscript). The distinction that shows is the size
 * cascade: a superscript is smaller, a superscript of a superscript smaller again, and then it stops.
 * Letting it shrink forever is how deeply nested exponents become an unreadable smudge.
 *
 * @param family font family for the whole formula
 * @param baseSize size at level 0, in points
 * @param level 0 = normal, 1 = script, 2 = scriptscript; never more
 */
public record MathStyle(String family, double baseSize, int level) {

    /** TeX's own ratios: 100%, then 70%, then 50%, and no further. */
    private static final double[] SCALE = {1.0, 0.7, 0.5};

    public static final int MAX_LEVEL = SCALE.length - 1;

    /** A serif face, because mathematics is set in one and JavaFX's logical families are portable. */
    public static final String DEFAULT_FAMILY = "Serif";

    public static MathStyle of(double baseSize) {
        return new MathStyle(DEFAULT_FAMILY, baseSize, 0);
    }

    public MathStyle {
        if (level < 0 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("level out of range: " + level);
        }
    }

    /** Point size at this level. */
    public double size() {
        return baseSize * SCALE[level];
    }

    /**
     * The style for a fraction's numerator and denominator.
     *
     * <p>TeX shrinks these one step — but only below display style. At the top level they stay full
     * size, which is what makes a quotient on the stack readable; shrinking there makes every fraction
     * in the calculator look like a footnote.
     */
    public MathStyle fractionPart() {
        return level == 0 ? this : script();
    }

    /** One level smaller, stopping at scriptscript. */
    public MathStyle script() {
        return level >= MAX_LEVEL ? this : new MathStyle(family, baseSize, level + 1);
    }

    /** True in script or scriptscript, where TeX suppresses binary and relational spacing. */
    public boolean cramped() {
        return level > 0;
    }

    /**
     * Height of the maths axis above the baseline — where a fraction bar sits and a minus sign is
     * centred. Roughly a quarter of the size for a text face.
     */
    public double axisHeight() {
        return size() * 0.25;
    }

    /** Thickness of a fraction bar or the bar over a radical. TeX's default rule thickness is 0.04em. */
    public double ruleThickness() {
        return Math.max(1, size() * 0.045);
    }

    /** The unit TeX measures spacing in: 1em = the current size. */
    public double em() {
        return size();
    }

    /** Upright, for numbers, function names and punctuation. */
    public Font upright() {
        return Font.font(family, FontWeight.NORMAL, FontPosture.REGULAR, size());
    }

    /**
     * Italic, for variables.
     *
     * <p>This single distinction does more for whether output reads as typeset mathematics than
     * anything else here: {@code sin(x)} with an upright function name and an italic variable looks
     * right, and the same glyphs all italic look like a font sample.
     */
    public Font italic() {
        return Font.font(family, FontWeight.NORMAL, FontPosture.ITALIC, size());
    }
}
