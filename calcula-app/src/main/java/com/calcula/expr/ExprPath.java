package com.calcula.expr;

import java.util.ArrayList;
import java.util.List;

import com.calcula.expr.Expr.Call;

/**
 * The address of one subterm inside a formula: the argument index at each level, from the root down.
 *
 * <p>A path rather than the subterm itself, because a subterm does not identify itself. In
 * {@code x + x} the two operands are equal, so "the one that was clicked" cannot be expressed as an
 * {@link Expr} — and a rewrite driven by value would change both. Selecting the second {@code x} is
 * {@code [1]}; the root is the empty path.
 *
 * <p>This is what makes a rendered formula something to operate ON rather than only to read: pick a
 * part, transform that part, and {@link #replace} puts the result back where it came from.
 */
public final class ExprPath {

    /** The whole formula. */
    public static final List<Integer> ROOT = List.of();

    private ExprPath() {}

    /**
     * The subterm at {@code path}, or null when the path does not address one.
     *
     * <p>Null rather than an exception: a path is held across edits by the UI, and a stale one is an
     * ordinary event — the formula it addressed has been replaced — not a programming error.
     */
    public static Expr at(Expr root, List<Integer> path) {
        Expr current = root;
        for (int index : path) {
            if (!(current instanceof Call call) || index < 0 || index >= call.arity()) {
                return null;
            }
            current = call.arg(index);
        }
        return current;
    }

    /** Whether {@code path} addresses a subterm of {@code root}. */
    public static boolean isValid(Expr root, List<Integer> path) {
        return at(root, path) != null;
    }

    /**
     * {@code root} with the subterm at {@code path} replaced, or null when the path is stale.
     *
     * <p>Rebuilds only the spine down to the change — every {@link Expr} is immutable, so the parts of
     * the tree that were not touched are shared rather than copied.
     */
    public static Expr replace(Expr root, List<Integer> path, Expr replacement) {
        if (replacement == null || !isValid(root, path)) {
            return null;
        }
        return rebuild(root, path, 0, replacement);
    }

    private static Expr rebuild(Expr current, List<Integer> path, int depth, Expr replacement) {
        if (depth == path.size()) {
            return replacement;
        }
        Call call = (Call) current; // isValid already walked this
        int index = path.get(depth);
        List<Expr> args = new ArrayList<>(call.args());
        args.set(index, rebuild(call.arg(index), path, depth + 1, replacement));
        return Exprs.call(call.head(), args);
    }

    /** The path of the {@code index}-th argument of whatever {@code path} addresses. */
    public static List<Integer> child(List<Integer> path, int index) {
        List<Integer> extended = new ArrayList<>(path);
        extended.add(index);
        return List.copyOf(extended);
    }

    /** The enclosing subterm's path, or null at the root. */
    public static List<Integer> parent(List<Integer> path) {
        return path.isEmpty() ? null : List.copyOf(path.subList(0, path.size() - 1));
    }

    /**
     * The path of the sibling {@code by} places along, or null when there is none.
     *
     * <p>Takes the root because the answer depends on how many arguments the PARENT has, which a path
     * on its own does not know. Widening and narrowing walk the left edge of the tree; this is what
     * reaches the second argument of a function without going up and back down.
     */
    public static List<Integer> sibling(Expr root, List<Integer> path, int by) {
        List<Integer> up = parent(path);
        if (up == null) {
            return null; // the whole formula has no siblings
        }
        if (!(at(root, up) instanceof Call parent)) {
            return null;
        }
        int next = path.get(path.size() - 1) + by;
        return next < 0 || next >= parent.arity() ? null : child(up, next);
    }

    /**
     * How deep a path goes. Used to prefer the innermost of several nodes under one click.
     */
    public static int depth(List<Integer> path) {
        return path.size();
    }
}
