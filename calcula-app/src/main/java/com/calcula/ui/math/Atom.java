package com.calcula.ui.math;

/**
 * TeX's atom classes, and the spacing between them.
 *
 * <p>This is the rule most home-made math renderers skip, and skipping it is why their output reads as
 * a font sample rather than as typeset mathematics. The gap around {@code +} is not the gap around
 * {@code =} and neither is the gap in {@code f(x)} — the spacing is a property of what KIND of thing
 * sits either side, not of the characters.
 */
public enum Atom {
    /** An ordinary symbol: a variable, a number. */
    ORD,
    /** A large operator: sum, integral, and function names, which take a thin space after. */
    OP,
    /** A binary operator: {@code + - *}. */
    BIN,
    /** A relation: {@code = < > ≤}. */
    REL,
    /** An opening delimiter. */
    OPEN,
    /** A closing delimiter. */
    CLOSE,
    /** Punctuation: a comma. */
    PUNCT;

    // TeX's spacing units, in em. 1 mu = 1/18 em.
    private static final double THIN = 3.0 / 18.0;
    private static final double MEDIUM = 4.0 / 18.0;
    private static final double THICK = 5.0 / 18.0;

    /**
     * Space between two adjacent atoms, in em.
     *
     * <p>Medium and thick spaces vanish in script styles, which is TeX's rule and the reason a
     * superscript like {@code x^{a+b}} stays tight instead of sprawling.
     */
    public static double between(Atom left, Atom right, boolean scriptStyle) {
        double space = rawSpace(left, right);
        if (scriptStyle && space > THIN) {
            return 0;
        }
        return space;
    }

    private static double rawSpace(Atom left, Atom right) {
        if (left == PUNCT) {
            return THIN;
        }
        if (left == BIN || right == BIN) {
            // A binary operator needs a partner on both sides; next to an opening delimiter or another
            // operator it is really a sign, and TeX gives it no space at all.
            return isNude(left) || isNude(right) ? 0 : MEDIUM;
        }
        if (left == REL || right == REL) {
            return isNude(left) || isNude(right) ? 0 : THICK;
        }
        if (left == OP || right == OP) {
            return THIN;
        }
        return 0;
    }

    /** Positions where a binary operator has nothing to bind to on that side. */
    private static boolean isNude(Atom atom) {
        return atom == OPEN || atom == PUNCT;
    }
}
