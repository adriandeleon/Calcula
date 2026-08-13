package com.calcula.parse;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import com.calcula.machine.Modes;
import com.calcula.parse.Lexer.Kind;
import com.calcula.parse.Lexer.Token;

/**
 * Precedence-climbing parser for conventional mathematical notation — {@code sin(x)}, {@code x^2},
 * {@code [1, 2, 3]} — rather than the engine's Wolfram-flavoured surface syntax.
 *
 * <p>Writing our own rather than passing strings to Symja is what keeps the engine swappable and lets
 * the notation be the one people actually write. Function names are normalised to engine heads by
 * {@link Names} at parse time, so the tree is always engine-canonical.
 *
 * <h2>Precedence, loosest first</h2>
 *
 * <pre>
 *   1  = == &lt; &gt; &lt;= &gt;= !=   non-associative
 *   2  + -                     left
 *   3  * /                     left
 *   4  unary - +
 *   5  ^                       RIGHT
 *   6  postfix !
 * </pre>
 *
 * <p>Unary minus binding looser than {@code ^} is what makes {@code -2^2} equal −4, as everywhere else
 * in mathematics; and {@code ^} taking a unary operand on its right is what makes {@code 2^-3} parse.
 *
 * <p>There is deliberately no implicit multiplication: {@code 2x} is a syntax error rather than a guess,
 * because {@code f(x)} and {@code f (x)} would otherwise be a function call and a product depending on
 * whether {@code f} happens to be defined.
 */
public final class Parser {

    private final List<Token> tokens;
    private int i;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /** Parse a complete expression, or throw {@link ParseException}. */
    public static Expr parse(String source) {
        if (source == null || source.isBlank()) {
            throw new ParseException("empty expression", 0);
        }
        Parser p = new Parser(Lexer.tokenize(source));
        Expr e = p.expression();
        Token rest = p.peek();
        if (rest.kind() != Kind.EOF) {
            throw new ParseException("unexpected " + rest, rest.pos());
        }
        return e;
    }

    // ------------------------------------------------------------------ grammar

    private Expr expression() {
        return rule();
    }

    /**
     * {@code x -> 1}, the shape every {@code Solve} result comes back in. Right-associative and looser
     * than anything else, so {@code x -> a + b} groups the way it reads.
     */
    private Expr rule() {
        Expr left = relational();
        if (peek().kind() == Kind.OP && peek().text().equals("->")) {
            next();
            return Exprs.call("Rule", left, rule());
        }
        return left;
    }

    /**
     * {@code 2 +/- 0.1} — a measurement and how far out it might be.
     *
     * <p>Its own level, looser than {@code +} so {@code 2 + 3 +/- 0.1} is a measurement of five, and
     * tighter than a comparison so an error form can be compared with something. Non-associative:
     * {@code a +/- b +/- c} is not a thing anybody means.
     */
    private Expr errorForm() {
        Expr left = additive();
        if (peek().kind() == Kind.OP && peek().text().equals("+/-")) {
            next();
            return Exprs.call("PlusMinus", left, additive());
        }
        // A word rather than a punctuation mark, so it is matched on a SYMBOL token. Safe to claim:
        // `a mod b` is currently a parse error (there is no implicit multiplication), and the mod
        // FUNCTION is a symbol followed by an open paren, which never reaches here.
        if (peek().kind() == Kind.SYMBOL && peek().text().equals("mod")) {
            next();
            return Exprs.call("Modulo", left, additive());
        }
        if (peek().kind() == Kind.OP && peek().text().equals("..")) {
            next();
            // The ENGINE'S own shape, Interval({a, b}), and not a two-argument call: Interval(1, 2)
            // means two degenerate intervals to Symja, which would have been silently wrong rather
            // than an error. Building what it already understands is what makes its arithmetic --
            // including sin over a range, which no reimplementation here would match -- work for free.
            return Exprs.call("Interval", Exprs.list(left, additive()));
        }
        return left;
    }

    private Expr relational() {
        Expr left = errorForm();
        Token t = peek();
        if (t.kind() == Kind.OP) {
            String head =
                    switch (t.text()) {
                        case "=", "==" -> "Equal";
                        case "<" -> "Less";
                        case ">" -> "Greater";
                        case "<=" -> "LessEqual";
                        case ">=" -> "GreaterEqual";
                        case "!=" -> "Unequal";
                        default -> null;
                    };
            if (head != null) {
                next();
                // Non-associative on purpose: `a < b < c` means different things in different
                // languages, so it is refused rather than silently picking one.
                return Exprs.call(head, left, errorForm());
            }
        }
        return left;
    }

    private Expr additive() {
        Expr left = multiplicative();
        while (peek().kind() == Kind.OP
                && (peek().text().equals("+") || peek().text().equals("-"))) {
            String op = next().text();
            Expr right = multiplicative();
            left = Exprs.call(op.equals("+") ? "Plus" : "Subtract", left, right);
        }
        return left;
    }

    private Expr multiplicative() {
        Expr left = unary();
        while (peek().kind() == Kind.OP
                && (peek().text().equals("*") || peek().text().equals("/"))) {
            String op = next().text();
            Expr right = unary();
            left = Exprs.call(op.equals("*") ? "Times" : "Divide", left, right);
        }
        return left;
    }

    private Expr unary() {
        Token t = peek();
        if (t.kind() == Kind.OP && (t.text().equals("-") || t.text().equals("+"))) {
            next();
            Expr operand = unary();
            if (t.text().equals("+")) {
                return operand;
            }
            // Fold the sign into a literal so `-3` is the number −3 rather than Minus(3); it keeps the
            // tree small and makes numeric comparison in tests mean what it looks like.
            if (operand instanceof Expr.Int n) {
                return Exprs.of(n.value().negate());
            }
            if (operand instanceof Expr.Flt f) {
                return Exprs.of(f.value().negate());
            }
            return Exprs.call("Minus", operand);
        }
        return power();
    }

    private Expr power() {
        Expr base = postfix();
        if (peek().kind() == Kind.OP && peek().text().equals("^")) {
            next();
            // Right-associative, and the operand is a unary so `2^-3` parses.
            return Exprs.call("Power", base, unary());
        }
        return base;
    }

    private Expr postfix() {
        Expr e = atom();
        while (peek().kind() == Kind.OP && peek().text().equals("!")) {
            next();
            e = Exprs.call("Factorial", e);
        }
        return e;
    }

    private Expr atom() {
        Token t = next();
        switch (t.kind()) {
            case NUMBER -> {
                Expr n = number(t);
                Expr duration = hmsAfter(n);
                return duration != null ? duration : n;
            }
            case SYMBOL -> {
                if (peek().kind() == Kind.LPAREN) {
                    next();
                    List<Expr> args = arguments(Kind.RPAREN, ")");
                    return Exprs.call(Names.toHead(t.text()), args);
                }
                // Before the name table, and deliberately: a pattern is a shape rather than a name,
                // and nothing should be translating x_ as though somebody meant a function called x_.
                Expr pattern = Patterns.forName(t.text());
                return pattern != null ? pattern : Exprs.sym(Names.toHead(t.text()));
            }
            case LPAREN -> {
                Expr inner = expression();
                expect(Kind.RPAREN, ")");
                return inner;
            }
            case LBRACKET -> {
                return Exprs.list(arguments(Kind.RBRACKET, "]"));
            }
            case EOF -> throw new ParseException("expression ended unexpectedly", t.pos());
            default -> throw new ParseException("unexpected " + t, t.pos());
        }
    }

    /**
     * {@code 1@ 30' 0"} — hours, minutes and seconds, and any shorter tail of it.
     *
     * <p>A literal rather than an operator, which is why it is read here and not at a precedence level
     * the way {@code +/-} and {@code mod} are. There is nothing infix about it: the markers are
     * suffixes on the parts, and {@code 30'} on its own is as complete a duration as the whole thing.
     *
     * <p>Each part after the first must carry its marker. {@code 1@ 30} is refused rather than read as
     * an hour and a half, because there is no implicit multiplication here either — so the alternative
     * to a clear message is a parse error further along that says nothing about the real mistake.
     */
    private Expr hmsAfter(Expr first) {
        if (peek().kind() != Kind.OP) {
            return null;
        }
        return switch (peek().text()) {
            case "@" -> {
                next();
                Expr minutes = Exprs.ZERO;
                Expr seconds = Exprs.ZERO;
                if (peek().kind() == Kind.NUMBER) {
                    minutes = markedNumber("'");
                    if (peek().kind() == Kind.NUMBER) {
                        seconds = markedNumber("\"");
                    }
                }
                yield Exprs.call(HMS, first, minutes, seconds);
            }
            case "'" -> {
                next();
                Expr seconds = peek().kind() == Kind.NUMBER ? markedNumber("\"") : Exprs.ZERO;
                yield Exprs.call(HMS, Exprs.ZERO, first, seconds);
            }
            case "\"" -> {
                next();
                yield Exprs.call(HMS, Exprs.ZERO, Exprs.ZERO, first);
            }
            default -> null;
        };
    }

    /** The next number, which must be followed by {@code mark}. */
    private Expr markedNumber(String mark) {
        Expr value = number(next());
        Token t = peek();
        if (t.kind() != Kind.OP || !t.text().equals(mark)) {
            throw new ParseException("expected " + mark + " after this part of the time", t.pos());
        }
        next();
        return value;
    }

    /** The head a duration is held as, shared with the formatter and the evaluator. */
    public static final String HMS = "HMS";

    /** Comma-separated expressions up to a closing token, which is consumed. Handles the empty case. */
    private List<Expr> arguments(Kind closer, String closerText) {
        List<Expr> args = new ArrayList<>();
        if (peek().kind() == closer) {
            next();
            return args;
        }
        while (true) {
            args.add(expression());
            Token t = next();
            if (t.kind() == closer) {
                return args;
            }
            if (t.kind() != Kind.COMMA) {
                throw new ParseException("expected ',' or '" + closerText + "' but found " + t, t.pos());
            }
        }
    }

    private Expr number(Token t) {
        String text = t.text();
        int hash = text.indexOf('#');
        if (hash >= 0) {
            return radixNumber(text, hash, t);
        }
        // A decimal point or an exponent is the user asking for an inexact value; everything else
        // stays an exact integer, however long.
        boolean inexact = text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0;
        try {
            return inexact ? Exprs.of(new BigDecimal(text)) : Exprs.of(new BigInteger(text));
        } catch (NumberFormatException e) {
            throw new ParseException("malformed number '" + text + "'", t.pos());
        }
    }

    /**
     * {@code 16#ff} — a whole number written in a base.
     *
     * <p>Entry only. What comes back is an ordinary integer, so nothing downstream has to know this
     * spelling exists and the round trip is unaffected: the formatter writes 255 and the parser reads
     * it. Showing a number in a base is a display mode, and a different thing entirely.
     */
    private Expr radixNumber(String text, int hash, Token t) {
        try {
            int radix = Integer.parseInt(text.substring(0, hash));
            if (radix < Modes.MIN_RADIX || radix > Modes.MAX_RADIX) {
                throw new ParseException(
                        "base must be between " + Modes.MIN_RADIX + " and " + Modes.MAX_RADIX + ": " + text, t.pos());
            }
            return Exprs.of(new BigInteger(text.substring(hash + 1), radix));
        } catch (NumberFormatException e) {
            throw new ParseException("malformed number '" + text + "'", t.pos());
        }
    }

    // ------------------------------------------------------------------ tokens

    private Token peek() {
        return tokens.get(i);
    }

    private Token next() {
        Token t = tokens.get(i);
        if (t.kind() != Kind.EOF) {
            i++;
        }
        return t;
    }

    private void expect(Kind kind, String what) {
        Token t = next();
        if (t.kind() != kind) {
            throw new ParseException("expected '" + what + "' but found " + t, t.pos());
        }
    }
}
