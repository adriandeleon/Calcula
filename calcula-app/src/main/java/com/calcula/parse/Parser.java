package com.calcula.parse;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
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

    private Expr relational() {
        Expr left = additive();
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
                return Exprs.call(head, left, additive());
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
                return number(t);
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
        // A decimal point or an exponent is the user asking for an inexact value; everything else
        // stays an exact integer, however long.
        boolean inexact = text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0;
        try {
            return inexact ? Exprs.of(new BigDecimal(text)) : Exprs.of(new BigInteger(text));
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
