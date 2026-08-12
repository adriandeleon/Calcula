package com.calcula.ui;

import com.calcula.expr.Expr;
import com.calcula.parse.ParseException;
import com.calcula.parse.Parser;

/**
 * What the line currently on the input reads as, before anything is done with it.
 *
 * <p>Pure: it decides <b>what to show</b>, and the window decides how to draw it. The decision is
 * worth separating because it is where the judgement is — when to stay quiet, what an error should
 * say, and the deliberate refusal to guess in RPN.
 *
 * <p>It parses and does not evaluate, which is the whole safety property. Evaluating what someone is
 * halfway through typing would run a CAS on every keystroke, and worse, would run it on expressions
 * the user never asked for.
 */
public final class InputPreview {

    /** Nothing to show, and the strip should take no room at all. */
    public static final Preview QUIET = new Preview(null, null, 0);

    /**
     * @param parsed the expression the line reads as, or null
     * @param error what is wrong with it, or null
     * @param position where the error is, as an offset into the line
     */
    public record Preview(Expr parsed, String error, int position) {

        public boolean isQuiet() {
            return parsed == null && error == null;
        }
    }

    private InputPreview() {}

    /**
     * Read the line the way the given input model would.
     *
     * <p>Only algebraic entry gets a preview, and that is a decision rather than a gap. In RPN a line
     * is a <em>sequence</em> of operations — {@code 5 3 -} is three of them — so there is no single
     * expression to set, and showing the last token alone would be a smaller truth than saying
     * nothing. The stack is already the display for what RPN is doing.
     *
     * @param line what is on the input
     * @param readerId the active input model, {@code "algebraic"} or {@code "rpn"}
     */
    public static Preview of(String line, String readerId) {
        if (line == null || line.isBlank() || !"algebraic".equals(readerId)) {
            return QUIET;
        }
        try {
            return new Preview(Parser.parse(line), null, 0);
        } catch (ParseException e) {
            return new Preview(null, e.getMessage(), e.position());
        } catch (RuntimeException e) {
            // Anything the parser did not anticipate is not worth interrupting typing over: the strip
            // is an aid, and an aid that throws is worse than one that is briefly quiet.
            return QUIET;
        }
    }

    /**
     * The error as one line, with where it happened.
     *
     * <p>The position is given as a number rather than drawn as a caret under the column. A caret
     * would be the nicer thing and {@code ParseException} was built for it — but the input is a
     * single-line field that scrolls horizontally, so once the line is longer than the field, a
     * column-aligned caret points confidently at the wrong character. A number is right at every
     * length.
     */
    public static String message(Preview preview) {
        return preview.error() == null ? "" : preview.error() + "  (at " + preview.position() + ")";
    }
}
