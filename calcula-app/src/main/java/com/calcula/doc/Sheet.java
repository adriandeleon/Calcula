package com.calcula.doc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.calcula.expr.Expr;
import com.calcula.machine.CalcState;
import com.calcula.machine.Modes;
import com.calcula.machine.TrailEntry;

/**
 * A saved sheet: what was on the stack, what the variables held, the modes it was under, and the trail.
 *
 * <p>Distinct from {@link CalcState} because a saved document is not a machine state: the trail is part
 * of what you want back and no part of what the machine computes with, and the undo history is the
 * reverse — part of the machine and no part of the document. Persisting {@code CalcState} directly
 * would have tied the file format to a type whose shape is decided by the evaluator's needs.
 *
 * @param stack bottom-first, matching {@link CalcState}
 * @param variables named values, in insertion order so a file round-trips byte for byte
 * @param modes the angle unit, precision and so on the sheet was worked under
 * @param trail the running record; the sheet is still a sheet without it
 */
public record Sheet(List<Expr> stack, Map<String, Expr> variables, Modes modes, List<TrailEntry> trail) {

    /**
     * Bumped when the file format changes, so an older build never silently reinterprets a newer file.
     *
     * @see SheetFormat#read
     */
    public static final int SCHEMA_VERSION = 1;

    public static final Sheet EMPTY = new Sheet(List.of(), Map.of(), Modes.DEFAULTS, List.of());

    public Sheet {
        stack = List.copyOf(stack);
        // Insertion-ordered rather than Map.copyOf, whose iteration order is deliberately unspecified —
        // a file that reshuffles its own variables on every save is one that shows a diff every time.
        variables = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        trail = List.copyOf(trail);
        if (modes == null) {
            throw new IllegalArgumentException("a sheet with no modes");
        }
    }

    /**
     * The state a machine should be restored to.
     *
     * <p>The values come back; where they came from does not. Provenance is session-only — the
     * {@code .calc} format saves the mathematics, and a value's history is not part of the
     * mathematics. A loaded sheet therefore answers "where did this come from" with silence, which is
     * the truthful answer: it came from a file.
     *
     * <p>The alternative would be a second thing on every {@code stack} line, and the format rests on
     * Formatter and Parser being inverses — a line the parser cannot read back is data loss at SAVE
     * time, where the file looks healthy and the value returns wrong.
     */
    public CalcState state() {
        return CalcState.ofValues(stack, variables, modes);
    }

    public static Sheet of(CalcState state, List<TrailEntry> trail) {
        return new Sheet(state.stack(), state.variables(), state.modes(), trail);
    }

    /** Nothing worth saving: what a new sheet is, and what "no unsaved work" means. */
    public boolean isEmpty() {
        return stack.isEmpty() && variables.isEmpty() && trail.isEmpty();
    }
}
