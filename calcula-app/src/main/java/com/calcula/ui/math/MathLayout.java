package com.calcula.ui.math;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

import com.calcula.expr.Expr;
import com.calcula.expr.Expr.Call;
import com.calcula.expr.Expr.Flt;
import com.calcula.expr.Expr.Int;
import com.calcula.expr.Expr.Num;
import com.calcula.expr.Expr.Rat;
import com.calcula.expr.Expr.Sym;
import com.calcula.expr.Exprs;
import com.calcula.parse.Names;

/**
 * Renders an {@link Expr} as a tree of JavaFX nodes — real two-dimensional mathematics rather than a
 * line of text.
 *
 * <h2>Why nodes and not an image</h2>
 *
 * <p>Rendering to a bitmap (through LaTeX, say) is far less work and produces a flat picture. <b>Every
 * node here carries its own subexpression</b> in {@code getUserData()}, so a click can be resolved to
 * the exact subterm under it by {@link #exprAt}. That is what makes Calc's selection mode possible —
 * pick a subterm out of a formula and operate on only that part — and it is the one thing a rasterised
 * formula can never support.
 *
 * <h2>What makes it read as typeset</h2>
 *
 * <p>Three rules do most of the work, and all three are things a naive renderer skips:
 *
 * <ul>
 *   <li>Variables are <em>italic</em>, function names and numbers upright. {@code sin(x)} looks right;
 *       the same glyphs all italic look like a font sample.
 *   <li>Spacing comes from {@link Atom} classes, not from characters — the gap around {@code +} is not
 *       the gap around {@code =} nor the gap in {@code f(x)}.
 *   <li>Everything aligns on a <em>baseline</em>, and a fraction's baseline is nowhere near its bottom.
 *       Each composite node computes its own; see {@link FractionNode}.
 * </ul>
 *
 * <h2>Canonical forms</h2>
 *
 * <p>Results arrive from the engine with no subtraction, no division and no unary minus, and with n-ary
 * {@code Plus}/{@code Times}. The same reassembly the text formatter does happens here, for the same
 * reason: {@code Times(a, Power(b,-1))} has to become a built-up fraction, or every quotient renders as
 * a negative exponent.
 */
public final class MathLayout {

    /** Symbols that have a better glyph than their name. */
    private static final Map<String, String> GLYPHS = Map.of(
            "Pi", "π",
            "Infinity", "∞",
            "Degree", "°",
            "Alpha", "α",
            "Beta", "β",
            "Theta", "θ",
            "Lambda", "λ");

    private static final Map<String, String> RELATIONS = Map.of(
            "Equal", "=",
            "Less", "<",
            "Greater", ">",
            "LessEqual", "≤",
            "GreaterEqual", "≥",
            "Unequal", "≠",
            "Rule", "→");

    /** U+2212, the real minus sign — not a hyphen, which is shorter and sits at the wrong height. */
    private static final String MINUS = "−";

    private static final BigInteger MINUS_ONE = BigInteger.valueOf(-1);

    private MathLayout() {}

    /**
     * Render a whole formula, ready to drop into a scene.
     *
     * <p>Always returns a {@link Region}, wrapping a bare leaf in a row when it has to — so callers get
     * one shape to lay out regardless of whether the formula happened to be a single letter.
     */
    public static Region render(Expr e, MathStyle style) {
        Node node = layout(e, style);
        if (node instanceof Region region) {
            return region;
        }
        Region wrapper = (Region) row(style, new Item(node, Atom.ORD));
        wrapper.setUserData(e);
        return wrapper;
    }

    /**
     * The subexpression at a node, found by walking up until one is tagged.
     *
     * <p>Operator glyphs and spacers carry nothing, so clicking a {@code +} lands on the sum that
     * contains it rather than on nothing — which is the behaviour you want when selecting.
     */
    public static Expr exprAt(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n.getUserData() instanceof Expr e) {
                return e;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ dispatch

    private static Node layout(Expr e, MathStyle style) {
        Node node =
                switch (e) {
                    case Int n -> number(n.value().toString(), style);
                    case Flt f -> number(f.value().toPlainString(), style);
                    case Rat r -> rational(r, style);
                    case Sym s -> symbol(s, style);
                    case Call c -> call(c, style);
                };
        return tag(node, e);
    }

    private static Node tag(Node node, Expr e) {
        node.setUserData(e);
        return node;
    }

    private static Node call(Call c, MathStyle style) {
        return switch (c.head()) {
            case "Plus" -> c.arity() >= 2 ? sum(c, style) : function(c, style);
            case "Times" -> c.arity() >= 2 ? product(c, style) : function(c, style);
            case "Divide" -> c.arity() == 2 ? fraction(c.arg(0), c.arg(1), style) : function(c, style);
            case "Subtract" ->
                c.arity() == 2
                        ? row(style, item(c.arg(0), style), op(MINUS, style, Atom.BIN), item(c.arg(1), style))
                        : function(c, style);
            case "Minus" ->
                c.arity() == 1 ? row(style, op(MINUS, style, Atom.BIN), item(c.arg(0), style)) : function(c, style);
            case "Power" -> power(c, style);
            case "Sqrt" -> c.arity() == 1 ? new RadicalNode(layout(c.arg(0), style), style) : function(c, style);
            case "Factorial" ->
                c.arity() == 1 ? row(style, item(c.arg(0), style), op("!", style, Atom.ORD)) : function(c, style);
            case "List" -> list(c, style);
            case "$Engine" ->
                c.arity() == 1 && c.arg(0) instanceof Sym s ? number(s.name(), style) : function(c, style);
            default -> {
                String relation = RELATIONS.get(c.head());
                yield relation != null && c.arity() == 2
                        ? row(style, item(c.arg(0), style), op(relation, style, Atom.REL), item(c.arg(1), style))
                        : function(c, style);
            }
        };
    }

    // ------------------------------------------------------------------ leaves

    private static Node number(String text, MathStyle style) {
        Text t = new Text(text.replace("-", MINUS));
        t.setFont(style.upright());
        t.getStyleClass().add("math-text");
        return t;
    }

    private static Node symbol(Sym s, MathStyle style) {
        String glyph = GLYPHS.get(s.name());
        Text t = new Text(glyph != null ? glyph : Names.toDisplay(s.name()));
        // Variables italic, named constants upright — the same distinction print uses.
        t.setFont(glyph != null ? style.upright() : style.italic());
        t.getStyleClass().add("math-text");
        return t;
    }

    private static Node glyph(String text, MathStyle style, Atom atom) {
        Text t = new Text(text);
        t.setFont(style.upright());
        t.getStyleClass().add("math-text");
        return t;
    }

    /** An operator glyph together with the atom class that decides the space around it. */
    private static Item op(String text, MathStyle style, Atom atom) {
        return new Item(glyph(text, style, atom), atom);
    }

    private static Node rational(Rat r, MathStyle style) {
        boolean negative = r.num().signum() < 0;
        Node fraction = new FractionNode(
                number(r.num().abs().toString(), style.fractionPart()),
                number(r.den().toString(), style.fractionPart()),
                style);
        return negative ? row(style, op(MINUS, style, Atom.BIN), new Item(fraction, Atom.ORD)) : fraction;
    }

    // ------------------------------------------------------------------ composites

    private static Node sum(Call c, MathStyle style) {
        List<Item> items = new ArrayList<>();
        items.add(item(c.arg(0), style));
        for (Expr arg : c.args().subList(1, c.arity())) {
            Expr positive = negatedPart(arg);
            if (positive != null) {
                items.add(op(MINUS, style, Atom.BIN));
                items.add(item(positive, style));
            } else {
                items.add(op("+", style, Atom.BIN));
                items.add(item(arg, style));
            }
        }
        return row(style, items);
    }

    private static Node product(Call c, MathStyle style) {
        List<Expr> numerator = new ArrayList<>();
        List<Expr> denominator = new ArrayList<>();
        boolean negative = false;
        for (Expr arg : c.args()) {
            if (isMinusOne(arg)) {
                negative = !negative;
            } else if (arg instanceof Call p && "Power".equals(p.head()) && p.arity() == 2 && isMinusOne(p.arg(1))) {
                denominator.add(p.arg(0));
            } else if (arg instanceof Rat r) {
                if (r.num().signum() < 0) {
                    negative = !negative;
                }
                if (!r.num().abs().equals(BigInteger.ONE)) {
                    numerator.add(Exprs.of(r.num().abs()));
                }
                denominator.add(Exprs.of(r.den()));
            } else {
                numerator.add(arg);
            }
        }
        Node body = denominator.isEmpty()
                ? juxtapose(numerator, style)
                : new FractionNode(
                        juxtapose(numerator.isEmpty() ? List.of(Exprs.ONE) : numerator, style.fractionPart()),
                        juxtapose(denominator, style.fractionPart()),
                        style);
        return negative ? row(style, op(MINUS, style, Atom.BIN), new Item(body, Atom.ORD)) : body;
    }

    /**
     * Factors set side by side, the way {@code 2x} is written.
     *
     * <p>A dot is inserted only where juxtaposition would be read as something else — between two
     * numbers, where {@code 2 3} would look like twenty-three.
     */
    private static Node juxtapose(List<Expr> factors, MathStyle style) {
        if (factors.size() == 1) {
            return layout(factors.get(0), style);
        }
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < factors.size(); i++) {
            if (i > 0 && factors.get(i) instanceof Num) {
                items.add(op("·", style, Atom.BIN));
            }
            items.add(item(factors.get(i), style));
        }
        return row(style, items);
    }

    private static Node power(Call c, MathStyle style) {
        if (c.arity() != 2) {
            return function(c, style);
        }
        Expr exponent = c.arg(1);
        if (isMinusOne(exponent)) {
            return fraction(Exprs.ONE, c.arg(0), style);
        }
        if (isHalf(exponent)) {
            // A half power IS a square root, and reads far better drawn as one.
            return new RadicalNode(layout(c.arg(0), style), style);
        }
        Node base = needsFence(c.arg(0)) ? fenced(c.arg(0), style, FenceNode.Kind.PAREN) : layout(c.arg(0), style);
        return new ScriptNode(base, layout(exponent, style.script()), style);
    }

    private static Node fraction(Expr numerator, Expr denominator, MathStyle style) {
        return new FractionNode(
                layout(numerator, style.fractionPart()), layout(denominator, style.fractionPart()), style);
    }

    private static Node list(Call c, MathStyle style) {
        if (Exprs.isMatrix(c)) {
            List<List<Node>> rows = new ArrayList<>();
            for (Expr rowExpr : c.args()) {
                List<Node> cells = new ArrayList<>();
                for (Expr cell : Exprs.items(rowExpr)) {
                    cells.add(layout(cell, style));
                }
                rows.add(cells);
            }
            return new FenceNode(new MatrixNode(rows, style), style, FenceNode.Kind.BRACKET);
        }
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < c.arity(); i++) {
            if (i > 0) {
                items.add(op(",", style, Atom.PUNCT));
            }
            items.add(item(c.arg(i), style));
        }
        return new FenceNode(row(style, items), style, FenceNode.Kind.BRACKET);
    }

    /** A named function: an upright name and its parenthesised arguments. */
    private static Node function(Call c, MathStyle style) {
        Text name = new Text(Names.toDisplay(c.head()));
        name.setFont(style.upright());
        name.getStyleClass().add("math-text");

        List<Item> args = new ArrayList<>();
        for (int i = 0; i < c.arity(); i++) {
            if (i > 0) {
                args.add(op(",", style, Atom.PUNCT));
            }
            args.add(item(c.arg(i), style));
        }
        Node parenthesised = new FenceNode(row(style, args), style, FenceNode.Kind.PAREN);
        return row(style, new Item(name, Atom.OP), new Item(parenthesised, Atom.ORD));
    }

    private static Node fenced(Expr e, MathStyle style, FenceNode.Kind kind) {
        return new FenceNode(layout(e, style), style, kind);
    }

    /** A base that is itself compound needs bracketing before a superscript can go on it. */
    private static boolean needsFence(Expr e) {
        return e instanceof Call c
                && switch (c.head()) {
                    case "Plus", "Subtract", "Times", "Divide", "Minus", "Power" -> true;
                    default -> false;
                }
                && c.arity() >= 2;
    }

    // ------------------------------------------------------------------ rows

    private record Item(Node node, Atom atom) {}

    private static Item item(Expr e, MathStyle style) {
        Node node = layout(e, style);
        return new Item(node, atomOf(e));
    }

    private static Atom atomOf(Expr e) {
        if (e instanceof Call c) {
            if (RELATIONS.containsKey(c.head())) {
                return Atom.REL;
            }
            if ("Plus".equals(c.head()) || "Subtract".equals(c.head())) {
                return Atom.ORD;
            }
        }
        return Atom.ORD;
    }

    private static Node row(MathStyle style, Item... items) {
        return row(style, List.of(items));
    }

    private static Node row(MathStyle style, List<Item> items) {
        List<Node> pieces = new ArrayList<>(items.size());
        double[] gaps = new double[items.size()];
        for (int i = 0; i < items.size(); i++) {
            pieces.add(items.get(i).node());
            gaps[i] = i == 0
                    ? 0
                    : Atom.between(items.get(i - 1).atom(), items.get(i).atom(), style.cramped()) * style.em();
        }
        return new MathRow(pieces, gaps);
    }

    // ------------------------------------------------------------------ canonical forms

    private static Expr negatedPart(Expr e) {
        if (e instanceof Call c
                && "Times".equals(c.head())
                && c.arity() >= 2
                && c.arg(0) instanceof Num n
                && negative(n)) {
            List<Expr> rest = new ArrayList<>(c.args());
            Expr positive = negate(n);
            if (positive instanceof Int i && i.value().equals(BigInteger.ONE)) {
                rest.remove(0);
            } else {
                rest.set(0, positive);
            }
            return rest.size() == 1 ? rest.get(0) : Exprs.call("Times", rest);
        }
        if (e instanceof Num n && negative(n)) {
            return negate(n);
        }
        return null;
    }

    private static boolean negative(Num n) {
        return switch (n) {
            case Int i -> i.value().signum() < 0;
            case Rat r -> r.num().signum() < 0;
            case Flt f -> f.value().signum() < 0;
        };
    }

    private static Expr negate(Num n) {
        return switch (n) {
            case Int i -> Exprs.of(i.value().negate());
            case Rat r -> Exprs.rat(r.num().negate(), r.den());
            case Flt f -> Exprs.of(f.value().negate());
        };
    }

    /**
     * One half, however it was spelled.
     *
     * <p>Both forms genuinely occur: the ENGINE returns an exact {@code Rat(1,2)}, while the PARSER
     * gives {@code Divide(1, 2)} for a typed {@code x^(1/2)} — nothing has evaluated it yet. Matching
     * only the first draws a radical for engine output and a raised fraction for the identical thing
     * typed by hand.
     */
    private static boolean isHalf(Expr e) {
        if (e instanceof Rat r) {
            return r.num().equals(BigInteger.ONE) && r.den().equals(BigInteger.TWO);
        }
        return e instanceof Call c
                && "Divide".equals(c.head())
                && c.arity() == 2
                && c.arg(0) instanceof Int n
                && n.value().equals(BigInteger.ONE)
                && c.arg(1) instanceof Int d
                && d.value().equals(BigInteger.TWO);
    }

    private static boolean isMinusOne(Expr e) {
        return e instanceof Int i && i.value().equals(MINUS_ONE);
    }
}
