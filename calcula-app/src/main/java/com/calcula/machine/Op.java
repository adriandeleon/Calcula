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

    /** Push a stored value, or the bare symbol when nothing is bound to that name. */
    record Recall(String name) implements Op {
        public Recall {
            requireName(name);
        }
    }

    /** Replace the top value with the result of evaluating it again — Calc's {@code =}. */
    record Evaluate() implements Op {}

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
