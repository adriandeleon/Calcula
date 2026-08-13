package com.calcula.ui.math;

/**
 * Long numbers, grouped so they can be read.
 *
 * <p>{@code 18446744073709551615} is twenty digits nobody counts correctly on the first try.
 * {@code 18 446 744 073 709 551 615} is the same number and can be read.
 *
 * <p>The separator is a thin space, not a comma. A comma is a list separator everywhere else in this
 * window — inside {@code [1, 2, 3]}, inside {@code f(x, y)} — and using it inside a single number as
 * well would make {@code [1,234, 5]} genuinely ambiguous. A thin space cannot be mistaken for
 * anything, which is why it is the SI convention.
 *
 * <p>Display only. The value is untouched, so copying, exporting and saving all still produce the
 * digits a parser can read back.
 */
public final class DigitGroups {

    /** U+2009. Narrower than a word space, so a number still reads as one thing. */
    public static final String THIN_SPACE = " ";

    static final int GROUP = 3;

    /**
     * Below this, grouping is noise.
     *
     * <p>A year is four digits and wants to stay one word; {@code 2 026} reads as two numbers. Five is
     * where the SI convention starts and where a run of digits stops being countable at a glance.
     */
    static final int SHORTEST_GROUPED = 5;

    private DigitGroups() {}

    /**
     * Group the integer part of a number, or return it unchanged.
     *
     * <p>Unchanged is the answer for most of what arrives here: a symbol's name, an operator, a short
     * number. The check is a scan of a handful of characters and happens once per rendered number.
     */
    public static String group(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        int start = text.charAt(0) == '-' ? 1 : 0;
        int end = start;
        while (end < text.length() && isDigit(text.charAt(end))) {
            end++;
        }
        // Everything after the digits must be a decimal part or nothing at all. A name like "x1" or a
        // formatted quantity is not a number and is left exactly as it is.
        if (end - start < SHORTEST_GROUPED || (end < text.length() && text.charAt(end) != '.')) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + (end - start) / GROUP);
        out.append(text, 0, start);
        int firstGroup = (end - start) % GROUP;
        if (firstGroup == 0) {
            firstGroup = GROUP;
        }
        int at = start;
        while (at < end) {
            int take = at == start ? firstGroup : GROUP;
            if (at > start) {
                out.append(THIN_SPACE);
            }
            out.append(text, at, at + take);
            at += take;
        }
        // The fractional part is left alone: grouping after the point is a convention this window has
        // no need of, and a number that is grouped on both sides of a dot is harder to read, not
        // easier.
        out.append(text, end, text.length());
        return out.toString();
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
