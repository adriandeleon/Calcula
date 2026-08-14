package com.calcula.ui.math;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

import com.calcula.expr.Arith;
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
import com.calcula.hms.HmsForm;
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
            "Rule", "→",
            // Set as typed. A delayed rule has a glyph in Mathematica and no font here is sure to
            // carry it, and a missing glyph is a box — worse than the notation people type.
            "RuleDelayed", ":→",
            "Condition", "/;");

    /** U+2212, the real minus sign — not a hyphen, which is shorter and sits at the wrong height. */
    private static final String MINUS = "−";

    /** U+00B1. Typed as +/-, and there is no key for it. */
    private static final String PLUS_MINUS = "±";

    /** U+2009. A unit sits closer to its number than a word would. */
    private static final String THIN_SPACE = "\u2009";

    /** Two dots, set with the spacing of a binary operator. */
    private static final String RANGE = "..";

    private MathLayout() {}

    /**
     * Render a whole formula, ready to drop into a scene.
     *
     * <p>Always returns a {@link Region}, wrapping a bare leaf in a row when it has to — so callers get
     * one shape to lay out regardless of whether the formula happened to be a single letter.
     */
    /**
     * Set an expression that is a <b>reading</b> of a value rather than the value itself.
     *
     * <p>Nothing in the result is addressable. That is the point: the tree being drawn is not the one
     * on the stack, so a click resolving to a path inside it would hand a transform an address into
     * an expression that does not exist — an edit applied to something the user cannot see. Passing a
     * null root path is the same mechanism {@link #product} already uses for a reassembled product,
     * for the same reason.
     */
    public static Region renderReading(Expr e, MathStyle style) {
        return render(e, style, null);
    }

    public static Region render(Expr e, MathStyle style) {
        return render(e, style, ExprPath.ROOT);
    }

    private static Region render(Expr e, MathStyle style, List<Integer> root) {
        // Here rather than only in Themes.apply: a formula is also rendered into the offscreen scene
        // the clipboard picture uses, which never applies a theme. The check is a volatile read.
        com.calcula.ui.Fonts.load();
        Node node = layout(e, style, root);
        if (node instanceof Region region) {
            return region;
        }
        Region wrapper = (Region) row(style, new Item(node, Atom.ORD));
        wrapper.setUserData(e);
        // Only when there is an address to give it. A reading is not the value, so tagging its
        // wrapper at the root would make the one node that IS addressable point at the wrong tree.
        if (root != null) {
            wrapper.getProperties().put(PATH_KEY, root);
        }
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

    /**
     * The node drawn for {@code path} inside a rendering, or null when nothing is.
     *
     * <p>Null is an ordinary answer: a path can address a subterm the layout reassembled away, and a
     * selection can outlive the value it was made on. The caller shows nothing rather than guessing.
     *
     * <p>Deepest match wins. A node and its wrapper can both carry the same address — a fenced
     * argument is a FenceNode around the term — and highlighting the outer one would include the
     * brackets in something that is not bracketed.
     */
    public static Node nodeAt(Node rendered, List<Integer> path) {
        Node best = null;
        for (Node node : descendants(rendered)) {
            if (path.equals(node.getProperties().get(PATH_KEY)) && (best == null || depthOf(node) > depthOf(best))) {
                best = node;
            }
        }
        return best;
    }

    private static int depthOf(Node node) {
        int depth = 0;
        for (Node n = node; n != null; n = n.getParent()) {
            depth++;
        }
        return depth;
    }

    private static List<Node> descendants(Node root) {
        List<Node> all = new ArrayList<>();
        all.add(root);
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                all.addAll(descendants(child));
            }
        }
        return all;
    }

    // ------------------------------------------------------------------ dispatch

    /** Lay out a subterm whose address is not expressible — see {@link #selectionAt}. */
    private static Node layout(Expr e, MathStyle style) {
        return layout(e, style, null);
    }

    private static Node layout(Expr e, MathStyle style, List<Integer> path) {
        Node node =
                switch (e) {
                    // Grouped here rather than in the formatter: this is how the number is READ,
                    // and the formatter's job is producing something the parser can read back.
                    case Int n -> number(inBase(n.value(), style), style);
                    case Flt f -> number(DigitGroups.group(style.floats().format(f.value())), style);
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

    /** The {@code {a, b}} inside an interval, or null when this is not one we can draw. */
    private static Call intervalBounds(Call c) {
        return c.arity() == 1 && c.arg(0) instanceof Call pair && Exprs.isList(pair) && pair.arity() == 2 ? pair : null;
    }

    private static Node call(Call c, MathStyle style, List<Integer> path) {
        return switch (c.head()) {
            case "Plus" -> c.arity() >= 2 ? sum(c, style, path) : function(c, style, path);
            // Times is reassembled into a fraction or juxtaposed factors, so its parts are not at any
            // address — see selectionAt. A click inside one selects the whole product.
            case "Times" -> c.arity() >= 2 ? product(c, style, path) : function(c, style, path);
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
            // Typed +/- and set as one glyph: what a keyboard has and what mathematics looks like
            // are different things, and the formatter keeps the first while this shows the second.
            // Bracketed here and not in the formatter: [1 .. 2] is how an interval is written, and
            // `1 .. 2` is how it is typed. Same split as +/- against ±.
            case "Interval" ->
                intervalBounds(c) != null
                        ? new FenceNode(
                                row(
                                        style,
                                        item(intervalBounds(c).arg(0), style, null),
                                        op(RANGE, style, Atom.BIN),
                                        item(intervalBounds(c).arg(1), style, null)),
                                style,
                                FenceNode.Kind.BRACKET)
                        : function(c, style, path);
            // The value laid out properly and the unit set upright beside it. Upright because that is
            // what a unit is typographically — an italic m is a variable, an upright m is metres, and
            // the distinction is the whole convention. Rendered through the number path for exactly
            // that reason, rather than through symbol(), which italicises.
            case "Quantity" -> {
                if (c.arity() == 2 && c.arg(0) instanceof Expr.Num && c.arg(1) instanceof Sym unit) {
                    yield row(
                            style,
                            item(c.arg(0), style, at(path, 0)),
                            new Item(number(THIN_SPACE + unit.name(), style), Atom.ORD));
                }
                yield function(c, style, path);
            }
            // Set as it is typed, unlike +/- against ±: the markers ARE the mathematical notation
            // here, and there is no second glyph to reach for.
            case "HMS" -> {
                HmsForm duration = duration(c);
                yield duration != null ? number(duration.format(), style) : function(c, style, path);
            }
            case "PlusMinus" ->
                c.arity() == 2
                        ? row(
                                style,
                                item(c.arg(0), style, at(path, 0)),
                                op(PLUS_MINUS, style, Atom.BIN),
                                item(c.arg(1), style, at(path, 1)))
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

    /** The three parts as a duration, or null when they are not all numbers. */
    private static HmsForm duration(Call c) {
        if (c.arity() != 3) {
            return null;
        }
        java.math.BigDecimal[] parts = new java.math.BigDecimal[3];
        for (int i = 0; i < 3; i++) {
            if (!(c.arg(i) instanceof Expr.Num n)) {
                return null;
            }
            parts[i] = Arith.toDecimal(n, Arith.DEFAULT_PRECISION);
        }
        return HmsForm.ofParts(parts[0], parts[1], parts[2]);
    }

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

    /**
     * A whole number, in the base the modes ask for.
     *
     * <p>Grouped only in base ten. Grouping in threes is a decimal convention — hexadecimal is read in
     * fours and binary in eights — and threes applied to hex would make it harder to read rather than
     * easier, which is the opposite of what grouping is for.
     */
    private static String inBase(java.math.BigInteger value, MathStyle style) {
        if (style.radix() == com.calcula.machine.Modes.DECIMAL) {
            return DigitGroups.group(value.toString());
        }
        return style.radix() + "#" + value.toString(style.radix());
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

    private static Node product(Call c, MathStyle style, List<Integer> path) {
        Canonical.Product product = Canonical.splitProduct(c.args());
        List<Expr> numerator = product.numerator().isEmpty() ? List.of(Exprs.ONE) : product.numerator();
        // The reassembly is a no-op surprisingly often — a plain product of ordinary factors comes back
        // as exactly its own arguments, in order — and in that case each factor really is at its own
        // address. Worth detecting, because x*sin(x) inside a function is the commonest shape there is,
        // and treating it as unaddressable makes the whole feature miss its best case.
        boolean untouched = !product.negative() && product.denominator().isEmpty() && numerator.equals(c.args());
        List<Integer> factorPath = untouched ? path : null;
        Node body = product.isFraction()
                ? new FractionNode(
                        juxtapose(numerator, style.fractionPart(), null),
                        juxtapose(product.denominator(), style.fractionPart(), null),
                        style)
                : juxtapose(numerator, style, factorPath);
        return product.negative() ? row(style, op(MINUS, style, Atom.BIN), new Item(body, Atom.ORD)) : body;
    }

    /**
     * Factors set side by side, the way {@code 2x} is written.
     *
     * <p>A dot is inserted only where juxtaposition would be read as something else — between two
     * numbers, where {@code 2 3} would look like twenty-three.
     */
    private static Node juxtapose(List<Expr> factors, MathStyle style, List<Integer> path) {
        if (factors.size() == 1) {
            return layout(factors.get(0), style, at(path, 0));
        }
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < factors.size(); i++) {
            if (i > 0 && factors.get(i) instanceof Num) {
                items.add(op("·", style, Atom.BIN));
            }
            // Addressable only when the product was NOT reassembled — see product().
            items.add(item(factors.get(i), style, at(path, i)));
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

    /**
     * A list of lists that is not a matrix, because it holds rules.
     *
     * <p>{@code solve} comes back as {@code [[x -> 2], [x -> 3]]}, which is a list of lists and so
     * satisfies {@link Exprs#isMatrix} — and was drawn inside matrix fences. Those fences are a claim:
     * they say linear algebra, and a set of solutions is not that. Nothing else in the window
     * distinguishes the two, so the reader is left to notice that a "matrix" whose entries are arrows
     * cannot be one.
     *
     * <p>Rules are a safe thing to key on, unlike the shape alone. {@code FactorInteger} also returns
     * pairs — {@code [[3, 1], [5, 1]]} — and that is genuinely indistinguishable from a 2x2 integer
     * matrix without knowing what produced it. Guessing there would mis-set real matrices, so it is
     * left alone; a rule, by contrast, is never an element of a matrix.
     */
    private static boolean holdsRules(Call c) {
        for (Expr row : Exprs.items(c)) {
            for (Expr cell : Exprs.items(row)) {
                if (cell instanceof Call inner && "Rule".equals(inner.head())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Node list(Call c, MathStyle style, List<Integer> path) {
        if (Exprs.isMatrix(c) && !holdsRules(c)) {
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
