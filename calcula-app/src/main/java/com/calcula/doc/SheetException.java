package com.calcula.doc;

/** A sheet that cannot be read, with a message naming the line so it can be fixed by hand. */
public class SheetException extends RuntimeException {

    public SheetException(String message) {
        super(message);
    }

    public SheetException(String message, Throwable cause) {
        super(message, cause);
    }
}
