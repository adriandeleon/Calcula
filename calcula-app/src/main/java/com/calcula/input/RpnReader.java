package com.calcula.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.calcula.machine.CalcState;
import com.calcula.machine.Op;
import com.calcula.parse.Names;
import com.calcula.parse.Parser;

/**
 * Postfix entry: {@code 5 3 -} is two, {@code 3 4 + 2 *} is fourteen.
 *
 * <p>Whitespace-separated words. A word is an operator, a stack word, a function name, or — failing all
 * of those — an expression to push. That last fallback is what lets {@code (x+1) 2 ^} work: anything the
 * parser understands can be a single operand, so RPN here is a way of sequencing operations rather than
 * a restriction on what a value may be.
 *
 * <p>Arity is fixed per function name rather than guessed from the stack. Guessing is how {@code 1 2 3
 * max} silently becomes {@code max(1, 2, 3)} on one occasion and {@code max(2, 3)} on another.
 */
public final class RpnReader implements Reader {

    /** Symbols that take two values off the stack. */
    private static final Map<String, String> BINARY_OPERATORS = Map.of(
            "+", "Plus",
            "-", "Subtract",
            "*", "Times",
            "/", "Divide",
            "^", "Power");

    /** Words that manipulate the stack rather than compute. */
    private static final Set<String> STACK_WORDS = Set.of("drop", "dup", "swap", "clear", "roll", "eval");

    /**
     * Functions callable by name, with the number of arguments they take here. Deliberately explicit:
     * several of these are variadic in the engine, and a postfix reader has no way to know where the
     * argument list was meant to start.
     */
    private static final Map<String, Integer> ARITY = Map.ofEntries(
            Map.entry("Sin", 1),
            Map.entry("Cos", 1),
            Map.entry("Tan", 1),
            Map.entry("ArcSin", 1),
            Map.entry("ArcCos", 1),
            Map.entry("ArcTan", 1),
            Map.entry("Sinh", 1),
            Map.entry("Cosh", 1),
            Map.entry("Tanh", 1),
            Map.entry("Exp", 1),
            Map.entry("Log", 1),
            Map.entry("Log10", 1),
            Map.entry("Sqrt", 1),
            Map.entry("Abs", 1),
            Map.entry("Sign", 1),
            Map.entry("Floor", 1),
            Map.entry("Ceiling", 1),
            Map.entry("Round", 1),
            Map.entry("Re", 1),
            Map.entry("Im", 1),
            Map.entry("Conjugate", 1),
            Map.entry("Factorial", 1),
            Map.entry("Minus", 1),
            Map.entry("Simplify", 1),
            Map.entry("Expand", 1),
            Map.entry("Factor", 1),
            Map.entry("Mod", 2),
            Map.entry("GCD", 2),
            Map.entry("LCM", 2),
            Map.entry("Binomial", 2),
            Map.entry("Dot", 2),
            Map.entry("Max", 2),
            Map.entry("Min", 2),
            Map.entry("D", 2),
            Map.entry("Integrate", 2));

    @Override
    public String id() {
        return "rpn";
    }

    @Override
    public String label() {
        return "rpn";
    }

    @Override
    public List<Op> read(String line, CalcState state) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        List<Op> ops = new ArrayList<>();
        for (String word : line.trim().split("\\s+")) {
            ops.add(operation(word));
        }
        return ops;
    }

    private Op operation(String word) {
        String binary = BINARY_OPERATORS.get(word);
        if (binary != null) {
            return new Op.Apply(binary, 2);
        }
        if (word.equals("!")) {
            return new Op.Apply("Factorial", 1);
        }
        if (STACK_WORDS.contains(word)) {
            return stackWord(word);
        }
        // A bare name might be a function to apply. `neg` is spelled out because `-` is already taken
        // by subtraction, which is the usual postfix ambiguity.
        String head = word.equals("neg") ? "Minus" : Names.toHead(word);
        Integer arity = ARITY.get(head);
        if (arity != null) {
            return new Op.Apply(head, arity);
        }
        return new Op.Push(Parser.parse(word));
    }

    private Op stackWord(String word) {
        return switch (word) {
            case "drop" -> new Op.Drop(1);
            case "dup" -> new Op.Dup(1);
            case "swap" -> new Op.Swap();
            case "clear" -> new Op.Clear();
            case "roll" -> new Op.Roll(3);
            case "eval" -> new Op.Evaluate();
            default -> throw new IllegalStateException("unhandled stack word " + word);
        };
    }
}
