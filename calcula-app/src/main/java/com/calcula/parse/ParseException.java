package com.calcula.parse;

/** A syntax error, carrying the offset it happened at so the echo area can point a caret at it. */
public class ParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int position;

    public ParseException(String message, int position) {
        super(message);
        this.position = position;
    }

    /** Zero-based offset into the source text. */
    public int position() {
        return position;
    }

    @Override
    public String toString() {
        return getMessage() + " (at " + position + ")";
    }
}
