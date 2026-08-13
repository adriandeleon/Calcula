package com.calcula.machine;

import com.calcula.expr.Expr;

/**
 * The vocabulary the machine understands.
 *
 * <p>This is the seam that keeps the input model an open question. The machine never sees a keystroke;
 * it sees operations. An RPN reader turns {@code +} into {@link Apply}{@code ("Plus", 2)}, an algebraic
 * reader turns {@code 2+3} into a single {@link Push} of the parsed tree, and neither is privileged.
 * Adding a third — a formula editor, a script, a remote client — is a new reader and nothing else.
 */
public sealed interface Op {

    /** Put a value on the stack. */
    record Push(Expr value) implements Op {}

    /** Remove {@code count} values from the top. Calc's {@code DEL}. */
    record Drop(int count) implements Op {
        public Drop {
            requirePositive(count, "drop count");
        }
    }

    /** Duplicate the top {@code count} values, preserving their order. Calc's bare {@code RET}. */
    record Dup(int count) implements Op {
        public Dup {
            requirePositive(count, "duplicate count");
        }
    }

    /** Exchange the top two values. Calc's {@code TAB}. */
    record Swap() implements Op {}

    /**
     * Roll the top {@code depth} values, moving the top one down to the bottom of that group. A
     * negative depth rolls the other way. Calc's {@code M-TAB}.
     */
    record Roll(int depth) implements Op {
        public Roll {
            if (depth == 0 || Math.abs(depth) < 2) {
                throw new IllegalArgumentException("roll depth must be at least 2 in magnitude");
            }
        }
    }

    /** Empty the stack. */
    record Clear() implements Op {}

    /**
     * Pop {@code arity} values, apply {@code head} to them, and push the evaluated result. The
     * arguments keep stack order, so {@code 5 3 Apply("Subtract", 2)} is {@code 5 - 3}.
     */
    record Apply(String head, int arity) implements Op {
        public Apply {
            if (head == null || head.isBlank()) {
                throw new IllegalArgumentException("blank head");
            }
            if (arity < 0) {
                throw new IllegalArgumentException("negative arity");
            }
        }
    }

    /** Pop the top value and bind it to a name. The value stays popped, as in Calc's {@code s t}. */
    record Store(String name) implements Op {
        public Store {
            requireName(name);
        }
    }

    /**
     * Forget a binding. Calc's {@code s u}.
     *
     * <p>A name nothing is bound to is an error rather than a silent success: the two look identical
     * afterwards, and the one you wanted to hear about is the typo.
     */
    record Unstore(String name) implements Op {
        public Unstore {
            requireName(name);
        }
    }

    /** Push a stored value, or the bare symbol when nothing is bound to that name. */
    record Recall(String name) implements Op {
        public Recall {
            requireName(name);
        }
    }

    /**
     * Replace a list on top of the stack with its elements. Calc's {@code v u}.
     *
     * <p>The other half — packing — needs no operation of its own: it is exactly {@link Apply} with
     * the list head, which already pops the right number of values, builds the call and carries the
     * provenance. Unpacking has no such spelling, because nothing else in the vocabulary turns one
     * value into many.
     */
    record Unpack() implements Op {}

    /** Replace the top value with the result of evaluating it again — Calc's {@code =}. */
    record Evaluate() implements Op {}

    /**
     * Replace the value at Calc position {@code position} (1 is the top).
     *
     * <p>The stack is a document, not a log, so editing an entry in place is an ordinary thing to
     * want — and it is what lets a transformation of one PART of an answer land back in the answer it
     * came from rather than piling a near-copy on top.
     *
     * <p>The value is stored <b>verbatim</b>, unlike {@link Push}, which evaluates. The caller has
     * already decided what this should be; re-evaluating could undo the very transform that was asked
     * for — factoring something the evaluator would immediately expand again.
     */
    record ReplaceAt(int position, Expr value) implements Op {
        public ReplaceAt {
            requirePositive(position, "stack position");
            if (value == null) {
                throw new IllegalArgumentException("null replacement");
            }
        }
    }

    /**
     * Replace the mode line.
     *
     * <p>An operation rather than a setter, so that a mode change is undoable like everything else.
     * Modes live in {@link CalcState}, and someone who switches to degrees, gets an answer they did not
     * expect and presses undo should get the mode back along with the answer.
     */
    record SetModes(Modes modes) implements Op {
        public SetModes {
            if (modes == null) {
                throw new IllegalArgumentException("null modes");
            }
        }
    }

    private static void requirePositive(int n, String what) {
        if (n < 1) {
            throw new IllegalArgumentException(what + " must be at least 1");
        }
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("blank variable name");
        }
    }
}
