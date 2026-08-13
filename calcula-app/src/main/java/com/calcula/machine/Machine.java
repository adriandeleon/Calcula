package com.calcula.machine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import com.calcula.parse.Formatter;

/**
 * The calculator itself: a stack, a trail, and undo.
 *
 * <p>It knows nothing about keystrokes, windows or the CAS. It applies {@link Op}s, which is what keeps
 * the input model — RPN, algebraic, or something later — a choice rather than a commitment.
 *
 * <h2>Atomicity</h2>
 *
 * <p>An operation either happens completely or not at all. Every {@code apply} computes the next state
 * before touching anything, so a stack underflow or an engine failure leaves the machine exactly as it
 * was. Half-applying is how a calculator ends up with a value silently consumed by an operation that
 * then failed — the user sees the error, presses undo, and gets a state that never existed.
 *
 * <h2>Undo</h2>
 *
 * <p>A bounded deque of previous {@link CalcState}s rather than a log of inverse operations. States are
 * immutable, so a snapshot is a reference; inverse operations would need writing once per command, and
 * the one nobody wrote is found by a user rather than a test.
 *
 * <p>Not thread-safe, and not meant to be: the application runs it on a single worker thread.
 */
public final class Machine {

    /** Deep enough that nobody reaches the end by accident, bounded so a long session cannot grow forever. */
    public static final int MAX_UNDO = 200;

    private static final int MAX_TRAIL = 1000;

    private final Evaluator evaluator;
    private final Deque<CalcState> undoStack = new ArrayDeque<>();
    private final Deque<CalcState> redoStack = new ArrayDeque<>();
    private final List<TrailEntry> trail = new ArrayList<>();

    private CalcState state;

    public Machine(Evaluator evaluator) {
        this(evaluator, CalcState.EMPTY);
    }

    public Machine(Evaluator evaluator, CalcState initial) {
        this.evaluator = evaluator == null ? Evaluator.IDENTITY : evaluator;
        this.state = initial;
    }

    public CalcState state() {
        return state;
    }

    public List<Expr> stack() {
        return state.stack();
    }

    public Modes modes() {
        return state.modes();
    }

    public List<TrailEntry> trail() {
        return Collections.unmodifiableList(trail);
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    // ------------------------------------------------------------------ applying

    /**
     * Apply one operation. Throws {@link MachineException} without changing anything when it cannot be
     * done.
     */
    public void apply(Op op) {
        CalcState next = compute(op);
        commit(next);
    }

    /** Apply several operations as ONE undo step, which is what a reader emitting a burst wants. */
    public void applyAll(List<Op> ops) {
        if (ops.isEmpty()) {
            return;
        }
        CalcState before = state;
        CalcState working = state;
        try {
            for (Op op : ops) {
                working = computeFrom(working, op);
            }
        } catch (RuntimeException e) {
            state = before; // computeFrom never mutates, but be explicit about the guarantee
            throw e;
        }
        commit(working);
    }

    private void commit(CalcState next) {
        if (next == state) {
            return;
        }
        undoStack.push(state);
        while (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast();
        }
        redoStack.clear(); // a new action abandons the redo branch, as everywhere else
        state = next;
    }

    private CalcState compute(Op op) {
        return computeFrom(state, op);
    }

    private CalcState computeFrom(CalcState from, Op op) {
        return switch (op) {
            // The origin is what was ASKED, before evaluation: type 1/3 + 1/6 and the value is 1/2
            // while the origin is the sum. That is the pair every permutation below then carries
            // without knowing it is doing so.
            case Op.Push p ->
                from.withEntries(append(from, CalcState.Entry.from(evaluate(p.value(), from.modes()), p.value())));
            case Op.Drop d -> {
                List<CalcState.Entry> next = from.mutableEntries();
                require(from, d.count());
                next.subList(next.size() - d.count(), next.size()).clear();
                yield from.withEntries(next);
            }
            case Op.Dup d -> {
                List<CalcState.Entry> next = from.mutableEntries();
                next.addAll(from.topEntries(d.count()));
                yield from.withEntries(next);
            }
            case Op.Swap ignored -> {
                require(from, 2);
                List<CalcState.Entry> next = from.mutableEntries();
                Collections.swap(next, next.size() - 1, next.size() - 2);
                yield from.withEntries(next);
            }
            case Op.Roll r -> {
                int depth = Math.abs(r.depth());
                require(from, depth);
                List<CalcState.Entry> next = from.mutableEntries();
                List<CalcState.Entry> group = next.subList(next.size() - depth, next.size());
                // A positive depth moves the top value down to the bottom of the group.
                Collections.rotate(group, r.depth() > 0 ? 1 : -1);
                yield from.withEntries(next);
            }
            case Op.Clear ignored -> from.withEntries(List.of());
            case Op.Apply a -> {
                require(from, a.arity());
                List<CalcState.Entry> next = from.mutableEntries();
                List<Expr> args = next.subList(next.size() - a.arity(), next.size()).stream()
                        .map(CalcState.Entry::value)
                        .toList();
                Expr call = Exprs.call(a.head(), args);
                next.subList(next.size() - a.arity(), next.size()).clear();
                // The call built from the operands is exactly what this result was worked out from.
                next.add(CalcState.Entry.from(evaluate(call, from.modes()), call));
                yield from.withEntries(next);
            }
            case Op.Store s -> {
                require(from, 1);
                List<CalcState.Entry> next = from.mutableEntries();
                Expr value = next.remove(next.size() - 1).value();
                yield from.withEntries(next).withVariable(s.name(), value);
            }
            case Op.Recall r -> {
                Expr value = from.variables().get(r.name());
                // An unbound name is not an error: pushing the bare symbol is what lets you build an
                // expression in terms of something you have not defined yet.
                Expr name = Exprs.sym(r.name());
                yield from.withEntries(append(from, CalcState.Entry.from(value == null ? name : value, name)));
            }
            case Op.ReplaceAt r -> {
                require(from, r.position());
                List<CalcState.Entry> next = from.mutableEntries();
                next.set(next.size() - r.position(), CalcState.Entry.from(r.value(), r.value()));
                yield from.withEntries(next);
            }
            case Op.SetModes m -> {
                // Same modes is not an operation: it would push an undo entry that changes nothing,
                // and someone pressing "degrees" twice would then need two undos to get back.
                yield from.modes().equals(m.modes()) ? from : from.withModes(m.modes());
            }
            case Op.Evaluate ignored -> {
                require(from, 1);
                List<CalcState.Entry> next = from.mutableEntries();
                // What it was before is what it came from, which is the honest answer for a value
                // that has just been worked out further.
                Expr was = next.get(next.size() - 1).value();
                next.set(next.size() - 1, CalcState.Entry.from(evaluate(was, from.modes()), was));
                yield from.withEntries(next);
            }
        };
    }

    private Expr evaluate(Expr e, Modes modes) {
        try {
            Expr result = evaluator.evaluate(e, modes);
            return result == null ? e : result;
        } catch (MachineException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            String message = ex.getMessage();
            throw new MachineException(message == null || message.isBlank() ? ex.toString() : message, ex);
        }
    }

    private static List<CalcState.Entry> append(CalcState from, CalcState.Entry entry) {
        List<CalcState.Entry> next = from.mutableEntries();
        next.add(entry);
        return next;
    }

    private static void require(CalcState from, int count) {
        if (from.depth() < count) {
            throw new MachineException(
                    "needed " + count + " value" + (count == 1 ? "" : "s") + " but the stack holds " + from.depth());
        }
    }

    // ------------------------------------------------------------------ history

    /** Step back one operation. Returns false when there is nothing to undo. */
    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }
        redoStack.push(state);
        state = undoStack.pop();
        return true;
    }

    /** Step forward again after an undo. */
    public boolean redo() {
        if (redoStack.isEmpty()) {
            return false;
        }
        undoStack.push(state);
        state = redoStack.pop();
        return true;
    }

    // ------------------------------------------------------------------ documents

    /**
     * Replace everything: the state, the trail, and the history.
     *
     * <p>The history goes too, and must. Undo after opening a file would otherwise walk backwards into
     * the sheet that was there before — a stack from one document appearing inside another, which is
     * indistinguishable from corruption and impossible to explain.
     */
    public void restore(CalcState newState, List<TrailEntry> newTrail) {
        if (newState == null) {
            throw new IllegalArgumentException("restoring nothing");
        }
        state = newState;
        undoStack.clear();
        redoStack.clear();
        trail.clear();
        if (newTrail != null) {
            trail.addAll(
                    newTrail.size() > MAX_TRAIL
                            ? newTrail.subList(newTrail.size() - MAX_TRAIL, newTrail.size())
                            : newTrail);
        }
    }

    /** Start over from a given state, with an empty trail and no history. */
    public void reset(CalcState initial) {
        restore(initial, List.of());
    }

    // ------------------------------------------------------------------ trail

    /** Record a line in the trail. The machine does not write to it itself; the caller decides what is worth keeping. */
    public void record(TrailEntry entry) {
        trail.add(entry);
        if (trail.size() > MAX_TRAIL) {
            trail.subList(0, trail.size() - MAX_TRAIL).clear();
        }
    }

    public void recordInput(String text) {
        record(new TrailEntry(TrailEntry.Kind.INPUT, text));
    }

    public void recordResult(Expr value) {
        record(new TrailEntry(TrailEntry.Kind.RESULT, Formatter.format(value)));
    }

    public void recordError(String message) {
        record(new TrailEntry(TrailEntry.Kind.ERROR, message));
    }
}
