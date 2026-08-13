package com.calcula.parse;

import com.calcula.expr.Expr;
import com.calcula.expr.Expr.Call;
import com.calcula.expr.Exprs;

/**
 * {@code x_}, and what it stands for.
 *
 * <p>A pattern is written the way every other structure in this tree is written — as a call. {@code x_}
 * is {@code Pattern(x, Blank())}, which is not a translation into the engine's language but a
 * coincidence worth having: it <em>is</em> the engine's own form, so a pattern reaches Symja through
 * the same totality that carries any unrecognised head, and the adapter needed no change at all.
 *
 * <p>The lexer already reads {@code x_} as one name, because an underscore is an identifier character.
 * So this is a rule about a name's spelling rather than a change to the grammar, and it keys off the
 * <b>trailing</b> underscores only: {@code my_var} is a variable, as it has always been.
 *
 * <p>Round-tripping matters more here than anywhere: a sheet saves a value by formatting it and loads
 * it by parsing that back, so a pattern that printed as {@code Pattern(x, Blank())} and could not be
 * read back would be data loss at save time.
 */
public final class Patterns {

    /** {@code _} — matches one thing. */
    public static final String BLANK = "Blank";
    /** {@code __} — matches one or more. */
    public static final String BLANK_SEQUENCE = "BlankSequence";
    /** {@code ___} — matches none or more. */
    public static final String BLANK_NULL_SEQUENCE = "BlankNullSequence";
    /** The engine's head for a named pattern. */
    public static final String PATTERN = "Pattern";

    private Patterns() {}

    /**
     * The expression a name stands for, or null when it is an ordinary name.
     *
     * <p>Null rather than the symbol itself, so the caller keeps its own name handling — this decides
     * one thing and does not quietly take over the general case.
     */
    public static Expr forName(String typed) {
        if (typed == null || !typed.endsWith("_")) {
            return null;
        }
        int underscores = 0;
        int at = typed.length();
        while (at > 0 && typed.charAt(at - 1) == '_') {
            underscores++;
            at--;
        }
        String blank = blankFor(underscores);
        if (blank == null) {
            return null; // four or more is not notation anyone means
        }
        String base = typed.substring(0, at);
        Expr any = Exprs.call(blank);
        return base.isEmpty() ? any : Exprs.call(PATTERN, Exprs.sym(base), any);
    }

    private static String blankFor(int underscores) {
        return switch (underscores) {
            case 1 -> BLANK;
            case 2 -> BLANK_SEQUENCE;
            case 3 -> BLANK_NULL_SEQUENCE;
            default -> null;
        };
    }

    /** How a pattern is written back, or null when this expression is not one. */
    public static String write(Call c) {
        String underscores = underscoresFor(c.head());
        if (underscores != null && c.arity() == 0) {
            return underscores;
        }
        if (!PATTERN.equals(c.head()) || c.arity() != 2) {
            return null;
        }
        if (!(c.arg(0) instanceof Expr.Sym name) || !(c.arg(1) instanceof Call blank) || blank.arity() != 0) {
            return null;
        }
        String tail = underscoresFor(blank.head());
        return tail == null ? null : name.name() + tail;
    }

    private static String underscoresFor(String head) {
        return switch (head) {
            case BLANK -> "_";
            case BLANK_SEQUENCE -> "__";
            case BLANK_NULL_SEQUENCE -> "___";
            default -> null;
        };
    }
}
