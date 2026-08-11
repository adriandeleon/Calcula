package com.calcula.cas;

/** Anything the CAS could not do: a parse error, an unsupported operation, or a missing engine. */
public class CasException extends Exception {

    private static final long serialVersionUID = 1L;

    public CasException(String message) {
        super(message);
    }

    public CasException(String message, Throwable cause) {
        super(message, cause);
    }
}
