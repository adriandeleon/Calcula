package com.calcula.machine;

/**
 * One line of the trail — Calc's running record of what happened, kept beside the stack.
 *
 * <p>The kind is carried separately from the text so the view can style it. Prefixing the string with
 * {@code "= "} or {@code "! "} at the point of recording would push a presentation decision into the
 * machine and make the entries awkward to search later.
 */
public record TrailEntry(Kind kind, String text) {

    public TrailEntry {
        if (kind == null) {
            throw new IllegalArgumentException("null trail kind");
        }
        text = text == null ? "" : text;
    }

    public enum Kind {
        /** What the user typed. */
        INPUT,
        /** What came back. */
        RESULT,
        /** Why nothing came back. */
        ERROR,
        /** A note from the calculator itself — a mode change, a stored variable. */
        NOTE
    }
}
