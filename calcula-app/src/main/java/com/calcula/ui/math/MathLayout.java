package com.calcula.ui.math;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

import com.calcula.expr.Canonical;
import com.calcula.expr.Expr;
import com.calcula.expr.Expr.Call;
import com.calcula.expr.Expr.Flt;
import com.calcula.expr.Expr.Int;
import com.calcula.expr.Expr.Num;
import com.calcula.expr.Expr.Rat;
import com.calcula.expr.Expr.Sym;
import com.calcula.expr.ExprPath;
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

    private MathLayout() {}

    /**
     * Render a whole formula, ready to drop into a scene.
     *
     * <p>Always returns a {@link Region}, wrapping a bare leaf in a row when it has to — so callers get
     * one shape to lay out regardless of whether the formula happened to be a single letter.
     */
    public static Region render(Expr e, MathStyle style) {
        Node node = layout(e, style, ExprPath.ROOT);
        if (node instanceof Region region) {
            return region;
        }
        Region wrapper = (Region) row(style, new Item(node, Atom.ORD));
        wrapper.setUserData(e);
        wrapper.getProperties().put(PATH_KEY, ExprPath.ROOT);
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

    /** A subterm together with its address inside the formula it came from. */
    public record Selection(Expr expr, List<Integer> path) {}

    /**
     * The nearest node that is both a subterm AND addressable, walking up from {@code node}.
     *
     * <p>The two conditions are one lookup on purpose. A rendered formula does not mirror its tree:
     * canonical forms are reassembled for display, so a fraction can be synthesised from {@code Times},
     * a radical from {@code Power(x, 1/2)}, and a minus sign lifted out of a coefficient. Those nodes
     * show a subterm that is not <em>at</em> any address — {@code x*cos(x)} drawn after a minus is not
     * what {@code arg(0)} holds, which is {@code Times(-1, x, cos(x))}.
     *
     * <p>So a synthesised node carries no address, and this walks past it to the nearest ancestor that
     * has one. Returning the expr and the path from the SAME node makes them agree by construction —
     * the alternative, resolving each separately and checking afterwards, is a rewrite that silently
     * replaces the wrong thing whenever the check is forgotten.
     */
    public static Selection selectionAt(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n.getUserData() instanceof Expr e && n.getProperties().get(PATH_KEY) instanceof List<?> path) {
                @SuppressWarnings("unchecked")
                List<Integer> address = (List<Integer>) path;
                return new Selection(e, address);
            }
        }
        return null;
    }

    /** Property key for a node's address. Identity-keyed, so nothing else can collide with it. */
    private static final Object PATH_KEY = new Object();

    // ------------------------------------------------------------------ dispatch

    /** Lay out a subterm whose address is not expressible — see {@link #selectionAt}. */
    private static Node layout(Expr e, MathStyle style) {
        return layout(e, style, null);
    }

    private static Node layout(Expr e, MathStyle style, List<Integer> path) {
        Node node =
                switch (e) {
                    case Int n -> number(n.value().toString(), style);
                    case Flt f -> number(f.value().toPlainString(), style);
                    case Rat r -> rational(r, style);
                    case Sym s -> symbol(s, style);
                    case Call c -> call(c, style, path);
                };
        return tag(node, e, path);
    }

    private static Node tag(Node node, Expr e, List<Integer> path) {
        node.setUserData(e);
        if (path != null) {
            node.getProperties().put(PATH_KEY, path);
        }
        return node;
    }

    private static Node call(Call c, MathStyle style, List<Integer> path) {
        return switch (c.head()) {
            case "Plus" -> c.arity() >= 2 ? sum(c, style, path) : function(c, style, path);
            // Times is reassembled into a fraction or juxtaposed factors, so its parts are not at any
            // address — see selectionAt. A click inside one selects the whole product.
            case "Times" -> c.arity() >= 2 ? product(c, style) : function(c, style, path);
            case "Divide" ->
                c.arity() == 2
                        ? fraction(c.arg(0), c.arg(1), style, at(path, 0), at(path, 1))
                        : function(c, style, path);
            case "Subtract" ->
                c.arity() == 2
                        ? row(
                                style,
                                item(c.arg(0), style, at(path, 0)),
                                op(MINUS, style, Atom.BIN),
                                item(c.arg(1), style, at(path, 1)))
                        : function(c, style, path);
            case "Minus" ->
                c.arity() == 1
                        ? row(style, op(MINUS, style, Atom.BIN), item(c.arg(0), style, at(path, 0)))
                        : function(c, style, path);
            case "Power" -> power(c, style, path);
            case "Sqrt" ->
                c.arity() == 1
                        ? new RadicalNode(layout(c.arg(0), style, at(path, 0)), style)
                        : function(c, style, path);
            case "Factorial" ->
                c.arity() == 1
                        ? row(style, item(c.arg(0), style, at(path, 0)), op("!", style, Atom.ORD))
                        : function(c, style, path);
            case "List" -> list(c, style, path);
            case "$Engine" ->
                c.arity() == 1 && c.arg(0) instanceof Sym s ? number(s.name(), style) : function(c, style, path);
            default -> {
                String relation = RELATIONS.get(c.head());
                yield relation != null && c.arity() == 2
                        ? row(
                                style,
                                item(c.arg(0), style, at(path, 0)),
                                op(relation, style, Atom.REL),
                                item(c.arg(1), style, at(path, 1)))
                        : function(c, style, path);
            }
        };
    }

    /** The address of the {@code index}-th argument, or null when the parent has none either. */
    private static List<Integer> at(List<Integer> path, int index) {
        return path == null ? null : ExprPath.child(path, index);
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

    private static Node sum(Call c, MathStyle style, List<Integer> path) {
        List<Item> items = new ArrayList<>();
        items.add(item(c.arg(0), style, at(path, 0)));
        for (int i = 1; i < c.arity(); i++) {
            Expr arg = c.arg(i);
            Expr positive = Canonical.negatedPart(arg);
            if (positive != null) {
                // What is drawn is the POSITIVE part, after a minus sign that was lifted out of the
                // coefficient — so the node shows something the argument does not hold, and carries no
                // address. Selecting inside it resolves to the sum, which is honest.
                items.add(op(MINUS, style, Atom.BIN));
                items.add(item(positive, style, null));
            } else {
                items.add(op("+", style, Atom.BIN));
                items.add(item(arg, style, at(path, i)));
            }
        }
        return row(style, items);
    }

    private static Node product(Call c, MathStyle style) {
        Canonical.Product product = Canonical.splitProduct(c.args());
        List<Expr> numerator = product.numerator().isEmpty() ? List.of(Exprs.ONE) : product.numerator();
        Node body = product.isFraction()
                ? new FractionNode(
                        juxtapose(numerator, style.fractionPart()),
                        juxtapose(product.denominator(), style.fractionPart()),
                        style)
                : juxtapose(numerator, style);
        return product.negative() ? row(style, op(MINUS, style, Atom.BIN), new Item(body, Atom.ORD)) : body;
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
            // Factors come from a reassembled product, so none of them is at an address.
            items.add(item(factors.get(i), style, null));
        }
        return row(style, items);
    }

    private static Node power(Call c, MathStyle style, List<Integer> path) {
        if (c.arity() != 2) {
            return function(c, style, path);
        }
        Expr exponent = c.arg(1);
        if (Canonical.isMinusOne(exponent)) {
            // Drawn as 1/base. The numerator is invented; the denominator really is argument 0.
            return fraction(Exprs.ONE, c.arg(0), style, null, at(path, 0));
        }
        if (Canonical.isHalf(exponent)) {
            // A half power IS a square root, and reads far better drawn as one.
            return new RadicalNode(layout(c.arg(0), style, at(path, 0)), style);
        }
        Node base = needsFence(c.arg(0))
                ? fenced(c.arg(0), style, FenceNode.Kind.PAREN, at(path, 0))
                : layout(c.arg(0), style, at(path, 0));
        return new ScriptNode(base, layout(exponent, style.script(), at(path, 1)), style);
    }

    private static Node fraction(
            Expr numerator, Expr denominator, MathStyle style, List<Integer> top, List<Integer> bottom) {
        return new FractionNode(
                layout(numerator, style.fractionPart(), top), layout(denominator, style.fractionPart(), bottom), style);
    }

    private static Node list(Call c, MathStyle style, List<Integer> path) {
        if (Exprs.isMatrix(c)) {
            List<List<Node>> rows = new ArrayList<>();
            for (int r = 0; r < c.arity(); r++) {
                List<Node> cells = new ArrayList<>();
                List<Expr> cellExprs = Exprs.items(c.arg(r));
                for (int col = 0; col < cellExprs.size(); col++) {
                    // A matrix is a list of lists, so a cell is two levels down.
                    cells.add(layout(cellExprs.get(col), style, at(at(path, r), col)));
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
            items.add(item(c.arg(i), style, at(path, i)));
        }
        return new FenceNode(row(style, items), style, FenceNode.Kind.BRACKET);
    }

    /** A named function: an upright name and its parenthesised arguments. */
    private static Node function(Call c, MathStyle style, List<Integer> path) {
        Text name = new Text(Names.toDisplay(c.head()));
        name.setFont(style.upright());
        name.getStyleClass().add("math-text");

        List<Item> args = new ArrayList<>();
        for (int i = 0; i < c.arity(); i++) {
            if (i > 0) {
                args.add(op(",", style, Atom.PUNCT));
            }
            args.add(item(c.arg(i), style, at(path, i)));
        }
        Node parenthesised = new FenceNode(row(style, args), style, FenceNode.Kind.PAREN);
        return row(style, new Item(name, Atom.OP), new Item(parenthesised, Atom.ORD));
    }

    private static Node fenced(Expr e, MathStyle style, FenceNode.Kind kind, List<Integer> path) {
        return new FenceNode(layout(e, style, path), style, kind);
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

    private static Item item(Expr e, MathStyle style, List<Integer> path) {
        return new Item(layout(e, style, path), atomOf(e));
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

}
