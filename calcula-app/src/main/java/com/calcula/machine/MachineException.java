package com.calcula.machine;

/**
 * Something the user asked for that cannot be done: a stack underflow, an unknown position, an engine
 * failure.
 *
 * <p>Unchecked on purpose. These are reported to the user in the echo area, not handled in code, and
 * making every {@code apply} call site wrap a try/catch would bury the one place that genuinely does.
 */
public class MachineException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MachineException(String message) {
        super(message);
    }

    public MachineException(String message, Throwable cause) {
        super(message, cause);
    }
}
