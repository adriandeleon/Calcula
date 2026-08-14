package com.calcula.parse;

import java.util.ArrayList;
import java.util.List;

/** Turns source text into tokens. Hand-rolled; the grammar is small enough that a generator would cost more. */
final class Lexer {

    enum Kind {
        NUMBER,
        SYMBOL,
        OP,
        LPAREN,
        RPAREN,
        LBRACKET,
        RBRACKET,
        COMMA,
        EOF
    }

    /** {@code text} is the literal source, {@code pos} its offset, so errors can point at it. */
    record Token(Kind kind, String text, int pos) {
        @Override
        public String toString() {
            return kind == Kind.EOF ? "end of input" : "'" + text + "'";
        }
    }

    private final String src;
    private int i;

    Lexer(String src) {
        this.src = src;
    }

    static List<Token> tokenize(String src) {
        return new Lexer(src).run();
    }

    private List<Token> run() {
        List<Token> out = new ArrayList<>();
        while (true) {
            skipSpace();
            if (i >= src.length()) {
                out.add(new Token(Kind.EOF, "", i));
                return out;
            }
            int start = i;
            char c = src.charAt(i);
            if (Character.isDigit(c) || (c == '.' && i + 1 < src.length() && Character.isDigit(src.charAt(i + 1)))) {
                out.add(new Token(Kind.NUMBER, number(), start));
            } else if (Character.isLetter(c) || c == '_' || c == '$') {
                out.add(new Token(Kind.SYMBOL, identifier(), start));
            } else {
                out.add(punctuation(start, c));
            }
        }
    }

    private void skipSpace() {
        while (i < src.length() && Character.isWhitespace(src.charAt(i))) {
            i++;
        }
    }

    private String number() {
        int start = i;
        while (i < src.length() && Character.isDigit(src.charAt(i))) {
            i++;
        }
        // Calc's radix notation: 16#ff, 2#1011. Taken here rather than in the parser because the
        // digits after the # are letters, and the tokeniser would otherwise split ff off as a name.
        if (i < src.length() && src.charAt(i) == '#') {
            int save = i;
            i++;
            int digits = i;
            while (i < src.length() && Character.isLetterOrDigit(src.charAt(i))) {
                i++;
            }
            if (i > digits) {
                return src.substring(start, i);
            }
            i = save; // a # with nothing usable after it is not ours
        }
        // Not a decimal point when another dot follows: 1..2 is a range, and reading it as `1.`
        // then `.2` would silently make two numbers out of one interval.
        if (i < src.length() && src.charAt(i) == '.' && !(i + 1 < src.length() && src.charAt(i + 1) == '.')) {
            i++;
            while (i < src.length() && Character.isDigit(src.charAt(i))) {
                i++;
            }
        }
        // An exponent only counts if digits actually follow, so `2e` lexes as 2 then the symbol e
        // rather than swallowing the e and then failing on a number with nothing after the exponent.
        if (i < src.length() && (src.charAt(i) == 'e' || src.charAt(i) == 'E')) {
            int save = i;
            i++;
            if (i < src.length() && (src.charAt(i) == '+' || src.charAt(i) == '-')) {
                i++;
            }
            if (i < src.length() && Character.isDigit(src.charAt(i))) {
                while (i < src.length() && Character.isDigit(src.charAt(i))) {
                    i++;
                }
            } else {
                i = save;
            }
        }
        return src.substring(start, i);
    }

    private String identifier() {
        int start = i;
        if (i < src.length() && src.charAt(i) == '$') {
            i++; // a stack reference: $ alone, or $ followed by a position
        }
        while (i < src.length() && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i) == '_')) {
            i++;
        }
        return src.substring(start, i);
    }

    private Token punctuation(int start, char c) {
        switch (c) {
            case '(' -> {
                i++;
                return new Token(Kind.LPAREN, "(", start);
            }
            case ')' -> {
                i++;
                return new Token(Kind.RPAREN, ")", start);
            }
            case '[' -> {
                i++;
                return new Token(Kind.LBRACKET, "[", start);
            }
            case ']' -> {
                i++;
                return new Token(Kind.RBRACKET, "]", start);
            }
            case ',' -> {
                i++;
                return new Token(Kind.COMMA, ",", start);
            }
            default -> {
                // Three characters before two, for the same reason two come before one: +/- would
                // otherwise lex as a plus and then a division by nothing.
                if (src.startsWith("..", i)) {
                    i += 2;
                    return new Token(Kind.OP, "..", start);
                }
                if (i + 2 < src.length() && src.startsWith("+/-", i)) {
                    i += 3;
                    return new Token(Kind.OP, "+/-", start);
                }
                // Two-character operators first, or <= lexes as < followed by =.
                if (i + 1 < src.length()) {
                    String two = src.substring(i, i + 2);
                    if (two.equals("<=")
                            || two.equals(">=")
                            || two.equals("!=")
                            || two.equals("==")
                            || two.equals("->")
                            // Both before the single-character pass, which would otherwise take the
                            // `/` of `/;` as a division and leave a semicolon nothing can use.
                            || two.equals(":>")
                            || two.equals("/;")) {
                        i += 2;
                        return new Token(Kind.OP, two, start);
                    }
                }
                // @ ' and " are the hms markers. Free to claim: none of them meant anything before,
                // there are no string literals here for " to open and no derivative notation for '.
                if ("+-*/^=<>!@'\"".indexOf(c) >= 0) {
                    i++;
                    return new Token(Kind.OP, String.valueOf(c), start);
                }
                throw new ParseException("unexpected character '" + c + "'", start);
            }
        }
    }
}
