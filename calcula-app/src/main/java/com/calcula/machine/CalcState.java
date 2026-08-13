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
 *
 * <p>An entry is a value <b>and where it came from</b>. Holding the pair in one list rather than in
 * two parallel ones is the whole reason provenance is not a source of bugs here: every operation the
 * machine performs is list surgery — remove the top two, swap them, rotate a group, clear a range —
 * and surgery on pairs carries the origin along without a single line of code that knows it exists.
 * Two parallel lists would mean the same twelve operations written twice, and the day they disagree
 * is the day a value shows somebody else's history.
 */
public record CalcState(List<Entry> entries, Map<String, Expr> variables, Modes modes) {

    /**
     * A value on the stack, and the expression it was worked out from.
     *
     * <p>{@code origin} is what was <em>asked</em>, before evaluation: enter {@code 1/3 + 1/6} and the
     * value is {@code 1/2} while the origin is the sum you typed. It is null when nothing knows —
     * a sheet loaded from a file, where the history was not saved with it.
     *
     * <p>An {@link Expr} rather than a string, because it is the same tree either way and structure
     * survives: the head of the origin is what tells a renderer that a list of pairs is a
     * factorisation rather than a matrix, which a printed string would have to be re-parsed to learn.
     */
    public record Entry(Expr value, Expr origin) {

        /** A value whose history is not known. */
        public static Entry of(Expr value) {
            return new Entry(value, null);
        }

        /** A value and what produced it. */
        public static Entry from(Expr value, Expr origin) {
            return new Entry(value, origin);
        }

        public Entry {
            if (value == null) {
                throw new IllegalArgumentException("a stack entry needs a value");
            }
        }
    }

    public static final CalcState EMPTY = new CalcState(List.of(), Map.of(), Modes.DEFAULTS);

    public CalcState {
        entries = List.copyOf(entries);
        // Insertion-ordered, NOT Map.copyOf, whose iteration order is deliberately unspecified. Sheet
        // already says this about the file; the state it is built from has to keep the same promise or
        // the promise is only true for a sheet nobody worked in. Binding n and then third and saving
        // wrote them in whichever order the copy happened to hash into, so a sheet reshuffled itself on
        // a save that changed nothing — which SheetFormatTest could not see, because it builds a Sheet
        // directly and never goes through a machine.
        variables = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }

    /** A state holding these values, with nothing known about where they came from. */
    public static CalcState ofValues(List<Expr> values, Map<String, Expr> variables, Modes modes) {
        return new CalcState(values.stream().map(Entry::of).toList(), variables, modes);
    }

    /**
     * The values alone, bottom-to-top.
     *
     * <p>Derived rather than stored, so there is one source of truth and no chance of a stack and its
     * origins drifting out of step. It is built per call, which is affordable because it is asked for
     * once per published state rather than once per keystroke.
     */
    public List<Expr> stack() {
        return entries.stream().map(Entry::value).toList();
    }

    public int depth() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** The value at Calc position {@code n}, where 1 is the top. */
    public Expr at(int n) {
        return entryAt(n).value();
    }

    /** The whole entry at Calc position {@code n}, where 1 is the top. */
    public Entry entryAt(int n) {
        if (n < 1 || n > entries.size()) {
            throw new MachineException("there is no stack entry " + n);
        }
        return entries.get(entries.size() - n);
    }

    /** The top {@code count} values in stack order (bottom-most of the group first). */
    public List<Expr> top(int count) {
        return topEntries(count).stream().map(Entry::value).toList();
    }

    public List<Entry> topEntries(int count) {
        if (count > entries.size()) {
            throw new MachineException(
                    "needed " + count + " value" + (count == 1 ? "" : "s") + " but the stack holds " + entries.size());
        }
        return List.copyOf(entries.subList(entries.size() - count, entries.size()));
    }

    public CalcState withEntries(List<Entry> next) {
        return new CalcState(next, variables, modes);
    }

    public CalcState withModes(Modes newModes) {
        return new CalcState(entries, variables, newModes);
    }

    public CalcState withVariable(String name, Expr value) {
        Map<String, Expr> updated = new LinkedHashMap<>(variables);
        updated.put(name, value);
        return new CalcState(entries, updated, modes);
    }

    /**
     * Without this binding.
     *
     * <p>Returns {@code this} when the name was not bound, so an unstore that changes nothing pushes
     * no undo step — the same rule {@link Machine} applies to setting the modes it already has.
     */
    public CalcState withoutVariable(String name) {
        if (!variables.containsKey(name)) {
            return this;
        }
        Map<String, Expr> updated = new LinkedHashMap<>(variables);
        updated.remove(name);
        return new CalcState(entries, updated, modes);
    }

    /** A mutable copy, for building the next state. */
    public List<Entry> mutableEntries() {
        return new ArrayList<>(entries);
    }
}
