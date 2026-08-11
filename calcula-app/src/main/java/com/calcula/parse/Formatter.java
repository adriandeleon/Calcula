package com.calcula.parse;

import java.util.stream.Collectors;

import com.calcula.expr.Expr;
import com.calcula.expr.Expr.Call;
import com.calcula.expr.Expr.Flt;
import com.calcula.expr.Expr.Int;
import com.calcula.expr.Expr.Rat;
import com.calcula.expr.Expr.Sym;
import com.calcula.expr.Exprs;

/**
 * Renders an {@link Expr} back to the notation {@link Parser} accepts, with the fewest parentheses that
 * still mean the same thing.
 *
 * <p>Minimal parenthesisation is not cosmetic: {@code a - (b - c)} and {@code a - b - c} are different
 * numbers, so a formatter that drops parens by precedence alone is a correctness bug, not an ugly one.
 * The rule is that a child needs bracketing when it binds more loosely than its parent, OR binds equally
 * but sits on the associativity's weak side — the right of a left-associative operator, the left of a
 * right-associative one.
 *
 * <p>The invariant worth holding is {@code parse(format(parse(s))) == parse(s)} — formatting is stable
 * and lossless over everything the parser can build. It is deliberately not stated over arbitrary trees,
 * because numeric literals normalise on the way back: a {@link Rat} prints as a quotient and re-parses
 * as a division, which the machine then evaluates to the same rational. Structure is what must survive
 * verbatim; number representation is the machine's business.
 *
 * <p>A negative literal is treated as though it had unary precedence, not atomic. Printing {@code
 * Factorial(-3)} as {@code -3!} would re-parse as {@code -(3!)}, since {@code !} binds tighter than the
 * sign — a wrong answer produced entirely by formatting.
 */
public final class Formatter {

    private static final int PREC_RELATION = 1;
    private static final int PREC_ADDITIVE = 2;
    private static final int PREC_MULTIPLICATIVE = 3;
    private static final int PREC_UNARY = 4;
    private static final int PREC_POWER = 5;
    private static final int PREC_POSTFIX = 6;
    private static final int PREC_ATOM = 100;

    private Formatter() {}

    public static String format(Expr e) {
        return write(e, 0, false);
    }

    private static String write(Expr e, int parentPrec, boolean weakSide) {
        return switch (e) {
            // A negative literal carries a leading sign, which binds as loosely as any unary minus.
            case Int n -> bracket(n.value().toString(), signPrec(n.value().signum(), PREC_ATOM), parentPrec, weakSide);
            case Flt f ->
                bracket(f.value().toPlainString(), signPrec(f.value().signum(), PREC_ATOM), parentPrec, weakSide);
            // A rational prints as a division, which is also how it parses back.
            case Rat r ->
                bracket(r.num() + "/" + r.den(), signPrec(r.num().signum(), PREC_MULTIPLICATIVE), parentPrec, weakSide);
            case Sym s -> Names.toDisplay(s.name());
            case Call c -> call(c, parentPrec, weakSide);
        };
    }

    private static int signPrec(int signum, int whenPositive) {
        return signum < 0 ? PREC_UNARY : whenPositive;
    }

    private static String call(Call c, int parentPrec, boolean weakSide) {
        String infix = infixOperator(c.head());
        if (infix != null && c.arity() == 2) {
            int prec = precedence(c.head());
            boolean rightAssociative = "Power".equals(c.head());
            String left = write(c.arg(0), prec, rightAssociative);
            String right = write(c.arg(1), prec, !rightAssociative);
            // Power reads better tight; everything else gets breathing room.
            String text = "Power".equals(c.head()) ? left + infix + right : left + " " + infix + " " + right;
            return bracket(text, prec, parentPrec, weakSide);
        }
        if ("Minus".equals(c.head()) && c.arity() == 1) {
            return bracket("-" + write(c.arg(0), PREC_UNARY, false), PREC_UNARY, parentPrec, weakSide);
        }
        if ("Factorial".equals(c.head()) && c.arity() == 1) {
            return bracket(write(c.arg(0), PREC_POSTFIX, false) + "!", PREC_POSTFIX, parentPrec, weakSide);
        }
        if (Exprs.LIST.equals(c.head())) {
            return c.args().stream().map(Formatter::format).collect(Collectors.joining(", ", "[", "]"));
        }
        return Names.toDisplay(c.head())
                + c.args().stream().map(Formatter::format).collect(Collectors.joining(", ", "(", ")"));
    }

    /** Wrap only when precedence demands it, or when sitting on the weak side of an equal-precedence parent. */
    private static String bracket(String text, int prec, int parentPrec, boolean weakSide) {
        boolean needed = prec < parentPrec || (prec == parentPrec && weakSide);
        return needed ? "(" + text + ")" : text;
    }

    private static String infixOperator(String head) {
        return switch (head) {
            case "Plus" -> "+";
            case "Subtract" -> "-";
            case "Times" -> "*";
            case "Divide" -> "/";
            case "Power" -> "^";
            case "Equal" -> "=";
            case "Less" -> "<";
            case "Greater" -> ">";
            case "LessEqual" -> "<=";
            case "GreaterEqual" -> ">=";
            case "Unequal" -> "!=";
            default -> null;
        };
    }

    private static int precedence(String head) {
        return switch (head) {
            case "Equal", "Less", "Greater", "LessEqual", "GreaterEqual", "Unequal" -> PREC_RELATION;
            case "Plus", "Subtract" -> PREC_ADDITIVE;
            case "Times", "Divide" -> PREC_MULTIPLICATIVE;
            case "Minus" -> PREC_UNARY;
            case "Power" -> PREC_POWER;
            case "Factorial" -> PREC_POSTFIX;
            default -> PREC_ATOM;
        };
    }
}
