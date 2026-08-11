package com.calcula.input;

import java.util.ArrayList;
import java.util.List;

import com.calcula.expr.Expr;
import com.calcula.expr.Expr.Call;
import com.calcula.expr.Expr.Sym;
import com.calcula.expr.Exprs;

/**
 * Resolves {@code $} stack references inside a typed expression — Calc's way of using what is already
 * on the stack from algebraic entry.
 *
 * <p>{@code $} is the top value, {@code $2} the one below it, and so on. Typing {@code $ + 1} takes the
 * top value, adds one, and leaves one value where there was one before; {@code $1 * $2} consumes two.
 *
 * <p>The count consumed is the DEEPEST position referenced, not the number of references. {@code $1 +
 * $1} mentions the top twice but consumes it once, and {@code $2 + 1} consumes two even though it never
 * mentions {@code $1} — because the stack cannot be popped out of the middle.
 */
public final class StackRefs {

    /** The most positions a single expression may reach down. Beyond this it is almost certainly a typo. */
    public static final int MAX_DEPTH = 99;

    private StackRefs() {}

    /** True for {@code $}, {@code $1}, {@code $2} … */
    public static boolean isReference(String name) {
        return position(name) > 0;
    }

    /**
     * The stack position a name refers to, or 0 when it is an ordinary symbol. Bare {@code $} is
     * position 1.
     */
    public static int position(String name) {
        if (name == null || name.length() < 1 || name.charAt(0) != '$') {
            return 0;
        }
        if (name.length() == 1) {
            return 1;
        }
        int value = 0;
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < '0' || c > '9') {
                return 0; // $foo is not a reference; leave it alone
            }
            value = value * 10 + (c - '0');
            if (value > MAX_DEPTH) {
                return 0;
            }
        }
        return value;
    }

    /** The deepest position referenced anywhere in the tree; 0 when there are none. */
    public static int deepest(Expr e) {
        return switch (e) {
            case Sym s -> position(s.name());
            case Call c -> {
                int max = 0;
                for (Expr arg : c.args()) {
                    max = Math.max(max, deepest(arg));
                }
                yield max;
            }
            default -> 0;
        };
    }

    /**
     * Replace every reference with the value at that position.
     *
     * @param values the stack, bottom-to-top, as {@link com.calcula.machine.CalcState#stack()} gives it
     */
    public static Expr substitute(Expr e, List<Expr> values) {
        return switch (e) {
            case Sym s -> {
                int n = position(s.name());
                yield n == 0 ? s : values.get(values.size() - n);
            }
            case Call c -> {
                List<Expr> args = new ArrayList<>(c.arity());
                for (Expr arg : c.args()) {
                    args.add(substitute(arg, values));
                }
                yield Exprs.call(c.head(), args);
            }
            default -> e;
        };
    }
}
