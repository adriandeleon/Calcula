package com.calcula.machine;

/**
 * How hard to work on an answer. Calc's {@code m s}.
 *
 * <p>Every level here is something this calculator can actually do, which is why there are five and
 * not Calc's exact list: a mode that displays and changes nothing is a wrong answer the user has been
 * told to expect.
 *
 * <ul>
 *   <li>{@link #NONE} — do not evaluate. Type {@code 2 + 3} and {@code 2 + 3} is what lands, which is
 *       what you want when the shape is the point and the engine keeps helpfully rearranging it.
 *   <li>{@link #NUMERIC} — arithmetic only, and never the engine. Fast, exact, and available with no
 *       CAS loaded at all.
 *   <li>{@link #DEFAULT} — arithmetic, then the engine. What this has always done.
 *   <li>{@link #ALGEBRAIC} — and then ask it to simplify.
 *   <li>{@link #EXTENDED} — and then ask it to try harder. Slow, and sometimes the only thing that
 *       collapses an expression to what a person would have written.
 * </ul>
 */
public enum Simplification {
    NONE("none", ""),
    NUMERIC("numeric", "num"),
    DEFAULT("default", ""),
    ALGEBRAIC("algebraic", "alg"),
    EXTENDED("extended", "ext");

    private final String id;
    private final String label;

    Simplification(String id, String label) {
        this.id = id;
        this.label = label;
    }

    /** The word this is saved as. */
    public String id() {
        return id;
    }

    /**
     * How it appears in the mode line.
     *
     * <p>Empty for {@link #DEFAULT}, which needs no announcement — and empty for {@link #NONE} too,
     * because that one announces itself: nothing evaluates, which is impossible to miss and impossible
     * to mistake for anything else.
     */
    public String label() {
        return label;
    }

    public static Simplification byId(String candidate) {
        for (Simplification level : values()) {
            if (level.id.equalsIgnoreCase(candidate)) {
                return level;
            }
        }
        return null;
    }
}
