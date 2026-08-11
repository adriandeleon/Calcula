package com.calcula.input;

import java.util.List;

import com.calcula.machine.CalcState;
import com.calcula.machine.Op;

/**
 * Turns a line the user typed into machine operations.
 *
 * <p>The input model is a choice, not a commitment. RPN and algebraic entry are two implementations of
 * this one interface, emitting the same {@link Op} vocabulary, and neither is the default — that
 * decision is deliberately still open.
 *
 * <p>Readers get the current {@link CalcState} because entry can refer to the stack: {@code $} is the
 * top value, {@code $2} the one below it. Resolving those here rather than inside the machine keeps the
 * machine's vocabulary small and means a reader that has no such notion pays nothing for it.
 */
public interface Reader {

    /** Stable identifier, e.g. {@code "rpn"}. */
    String id();

    /** How the mode line names it. */
    String label();

    /**
     * Read one line. Returns the operations to apply as a single undo step, or an empty list when the
     * line was blank.
     *
     * @throws com.calcula.parse.ParseException if the line cannot be read
     * @throws com.calcula.machine.MachineException if it refers to stack entries that are not there
     */
    List<Op> read(String line, CalcState state);
}
