package com.calcula.machine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.calcula.expr.Expr;

/**
 * The whole of the calculator's state, as one immutable value.
 *
 * <p>Immutability is what makes undo trivial and correct: a snapshot is the reference itself, so
 * {@link Machine} keeps a deque of previous states rather than a log of inverse operations. Inverse
 * operations are where undo bugs live — every new command needs one, and the one nobody wrote is
 * discovered by a user.
 *
 * <p>The stack runs bottom-to-top: index 0 is the bottom, the last element is the top, and an entry's
 * displayed Calc number is {@code size - index}. That matches how the window draws it, with entry
 * {@code 1:} nearest the input line.
 */
public record CalcState(List<Expr> stack, Map<String, Expr> variables, Modes modes) {

    public static final CalcState EMPTY = new CalcState(List.of(), Map.of(), Modes.DEFAULTS);

    public CalcState {
        stack = List.copyOf(stack);
        variables = Map.copyOf(variables);
    }

    public int depth() {
        return stack.size();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    /** The value at Calc position {@code n}, where 1 is the top. */
    public Expr at(int n) {
        if (n < 1 || n > stack.size()) {
            throw new MachineException("there is no stack entry " + n);
        }
        return stack.get(stack.size() - n);
    }

    /** The top {@code count} values in stack order (bottom-most of the group first). */
    public List<Expr> top(int count) {
        if (count > stack.size()) {
            throw new MachineException(
                    "needed " + count + " value" + (count == 1 ? "" : "s") + " but the stack holds " + stack.size());
        }
        return List.copyOf(stack.subList(stack.size() - count, stack.size()));
    }

    public CalcState withStack(List<Expr> newStack) {
        return new CalcState(newStack, variables, modes);
    }

    public CalcState withModes(Modes newModes) {
        return new CalcState(stack, variables, newModes);
    }

    public CalcState withVariable(String name, Expr value) {
        Map<String, Expr> updated = new LinkedHashMap<>(variables);
        updated.put(name, value);
        return new CalcState(stack, updated, modes);
    }

    /** A mutable copy of the stack, for building the next one. */
    public List<Expr> mutableStack() {
        return new ArrayList<>(stack);
    }
}
