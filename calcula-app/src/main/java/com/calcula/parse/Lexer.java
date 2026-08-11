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
            } else if (Character.isLetter(c) || c == '_') {
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
        if (i < src.length() && src.charAt(i) == '.') {
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
                // Two-character operators first, or <= lexes as < followed by =.
                if (i + 1 < src.length()) {
                    String two = src.substring(i, i + 2);
                    if (two.equals("<=") || two.equals(">=") || two.equals("!=") || two.equals("==")) {
                        i += 2;
                        return new Token(Kind.OP, two, start);
                    }
                }
                if ("+-*/^=<>!".indexOf(c) >= 0) {
                    i++;
                    return new Token(Kind.OP, String.valueOf(c), start);
                }
                throw new ParseException("unexpected character '" + c + "'", start);
            }
        }
    }
}
