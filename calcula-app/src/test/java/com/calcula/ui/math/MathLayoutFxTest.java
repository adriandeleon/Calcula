package com.calcula.ui.math;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.Text;

import com.calcula.expr.Expr;
import com.calcula.parse.Parser;
import com.calcula.ui.FxTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("fx")
class MathLayoutFxTest {

    private static final MathStyle STYLE = MathStyle.of(20);

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    /** Render source text and lay it out for real, so measurements mean something. */
    private static Region laidOut(String source) throws Exception {
        Region root = FxTestSupport.callOnFx(() -> MathLayout.render(Parser.parse(source), STYLE));
        return FxTestSupport.realize(root);
    }

    private static List<Node> descendants(Node root) {
        List<Node> all = new ArrayList<>();
        collect(root, all);
        return all;
    }

    private static void collect(Node node, List<Node> into) {
        into.add(node);
        if (node instanceof Parent p) {
            p.getChildrenUnmodifiable().forEach(child -> collect(child, into));
        }
    }

    private static boolean has(Node root, Class<?> type) {
        return descendants(root).stream().anyMatch(type::isInstance);
    }

    private static List<Text> texts(Node root) {
        return descendants(root).stream()
                .filter(Text.class::isInstance)
                .map(Text.class::cast)
                .toList();
    }

    // ---- structure ---------------------------------------------------------------------------

    @Test
    void aQuotientIsBuiltUpRatherThanWrittenInline() throws Exception {
        assertTrue(has(laidOut("(x+1)/(x-1)"), FractionNode.class));
    }

    @Test
    void anExponentBecomesARaisedScript() throws Exception {
        assertTrue(has(laidOut("x^2"), ScriptNode.class));
    }

    @Test
    void aSquareRootIsDrawnAsARadical() throws Exception {
        assertTrue(has(laidOut("sqrt(x)"), RadicalNode.class));
        // A half power IS a square root and reads far better drawn as one.
        assertTrue(has(laidOut("x^(1/2)"), RadicalNode.class));
    }

    @Test
    void listsAndMatricesGetBrackets() throws Exception {
        assertTrue(has(laidOut("[1, 2, 3]"), FenceNode.class));
        assertTrue(has(laidOut("[[1,2],[3,4]]"), MatrixNode.class));
    }

    @Test
    void theEnginesCanonicalFormsAreReassembled() throws Exception {
        // Results arrive as Times(a, Power(b, -1)) with no division at all. Rendering that literally
        // gives a negative exponent where the user wrote a quotient.
        Expr canonical = Parser.parse("a * b^(-1)");
        Region rendered = FxTestSupport.realize(FxTestSupport.callOnFx(() -> MathLayout.render(canonical, STYLE)));
        assertTrue(has(rendered, FractionNode.class), "a reciprocal factor should become a denominator");
        assertFalse(has(rendered, ScriptNode.class), "and not a raised -1");
    }

    // ---- the rules that make it read as typeset ----------------------------------------------

    @Test
    void variablesAreItalicAndFunctionNamesAreNot() throws Exception {
        // The single biggest signal that output is typeset rather than a font sample.
        List<Text> parts = texts(laidOut("sin(x)"));
        Text variable =
                parts.stream().filter(t -> t.getText().equals("x")).findFirst().orElseThrow();
        Text name = parts.stream()
                .filter(t -> t.getText().equals("sin"))
                .findFirst()
                .orElseThrow();

        assertEquals(FontPosture.ITALIC, posture(variable.getFont()), "a variable should be italic");
        assertEquals(FontPosture.REGULAR, posture(name.getFont()), "a function name should be upright");
    }

    private static FontPosture posture(Font font) {
        // JavaFX reports the resolved style in the font's style string.
        return font.getStyle().toLowerCase().contains("italic") ? FontPosture.ITALIC : FontPosture.REGULAR;
    }

    @Test
    void scriptsShrinkAndThenStopShrinking() throws Exception {
        Text base = texts(laidOut("x^2")).stream()
                .filter(t -> t.getText().equals("x"))
                .findFirst()
                .orElseThrow();
        Text exponent = texts(laidOut("x^2")).stream()
                .filter(t -> t.getText().equals("2"))
                .findFirst()
                .orElseThrow();
        assertTrue(exponent.getFont().getSize() < base.getFont().getSize(), "an exponent should be smaller");

        // Deeply nested exponents must stop shrinking, or they become an unreadable smudge.
        assertEquals(
                MathStyle.MAX_LEVEL, STYLE.script().script().script().script().level());
        assertTrue(STYLE.script().script().script().size() > 0);
    }

    @Test
    void spacingComesFromAtomClassesNotFromCharacters() throws Exception {
        // The gap around + is not the gap in a product; a sum should be strictly wider than the same
        // symbols juxtaposed.
        double sum = laidOut("a+b").prefWidth(-1);
        double product = laidOut("a*b").prefWidth(-1);
        assertTrue(sum > product, "a + b (" + sum + ") should be wider than ab (" + product + ")");
    }

    @Test
    void relationsGetMoreRoomThanBinaryOperators() throws Exception {
        assertTrue(laidOut("a=b").prefWidth(-1) > laidOut("a+b").prefWidth(-1));
    }

    @Test
    void aMinusSignIsTheRealCharacterNotAHyphen() throws Exception {
        // U+2212 sits at the right height and length; a hyphen does neither.
        assertTrue(texts(laidOut("a-b")).stream().anyMatch(t -> t.getText().equals("−")));
    }

    // ---- baselines ---------------------------------------------------------------------------

    @Test
    void aFractionsBaselineIsNearItsBarRatherThanItsBottom() throws Exception {
        Region rendered = laidOut("1/2");
        FractionNode fraction = (FractionNode) descendants(rendered).stream()
                .filter(FractionNode.class::isInstance)
                .findFirst()
                .orElseThrow();

        double baseline = FxTestSupport.callOnFx(fraction::getBaselineOffset);
        double height = FxTestSupport.callOnFx(() -> fraction.prefHeight(-1));

        assertTrue(baseline > 0 && baseline < height, "baseline " + baseline + " should sit inside " + height);
        // Near the middle, where the bar is — not at the bottom, which is what an unaware layout gives.
        assertTrue(Math.abs(baseline - height / 2) < height / 3, "baseline " + baseline + " of " + height);
    }

    @Test
    void aFractionInASumStaysOnTheSameLineAsTheRestOfIt() throws Exception {
        // The whole point of computing baselines: 1/2 + x must not sink relative to the x.
        Region rendered = laidOut("1/2 + x");
        Text variable = texts(rendered).stream()
                .filter(t -> t.getText().equals("x"))
                .findFirst()
                .orElseThrow();
        FractionNode fraction = (FractionNode) descendants(rendered).stream()
                .filter(FractionNode.class::isInstance)
                .findFirst()
                .orElseThrow();

        // boundsInParent.minY is the node's TOP for both kinds. Using layoutY instead double-counts
        // for a Text, whose layout origin already sits on its baseline when textOrigin is BASELINE —
        // the sort of arithmetic that makes a correct layout look broken.
        double fractionBaselineY =
                FxTestSupport.callOnFx(() -> fraction.getBoundsInParent().getMinY() + fraction.getBaselineOffset());
        double variableBaselineY =
                FxTestSupport.callOnFx(() -> variable.getBoundsInParent().getMinY() + variable.getBaselineOffset());

        assertEquals(fractionBaselineY, variableBaselineY, 1.5, "both baselines should land on the same line");
    }

    // ---- hit testing, which is the reason for nodes at all ------------------------------------

    @Test
    void everyRenderedPieceKnowsWhichSubexpressionItIs() throws Exception {
        Region rendered = laidOut("x^2 + 1");
        Expr whole = Parser.parse("x^2 + 1");

        Text variable = texts(rendered).stream()
                .filter(t -> t.getText().equals("x"))
                .findFirst()
                .orElseThrow();
        assertEquals(Parser.parse("x"), MathLayout.exprAt(variable), "a leaf should resolve to itself");

        Text exponent = texts(rendered).stream()
                .filter(t -> t.getText().equals("2"))
                .findFirst()
                .orElseThrow();
        assertEquals(Parser.parse("2"), MathLayout.exprAt(exponent));

        assertEquals(whole, MathLayout.exprAt(rendered), "the root should resolve to the whole formula");
    }

    @Test
    void clickingAnOperatorSelectsTheExpressionItBelongsTo() throws Exception {
        // Operator glyphs carry nothing of their own, so the walk up lands on the enclosing sum —
        // which is what you want when picking a subterm, rather than nothing at all.
        Region rendered = laidOut("x + 1");
        Text plus = texts(rendered).stream()
                .filter(t -> t.getText().equals("+"))
                .findFirst()
                .orElseThrow();

        Expr found = MathLayout.exprAt(plus);
        assertNotNull(found);
        assertEquals(Parser.parse("x + 1"), found);
    }

    @Test
    void aSubtermInsideAFractionIsReachable() throws Exception {
        Region rendered = laidOut("(a+b)/c");
        Text a = texts(rendered).stream()
                .filter(t -> t.getText().equals("a"))
                .findFirst()
                .orElseThrow();
        assertEquals(Parser.parse("a"), MathLayout.exprAt(a));
        // And its parent chain reaches the numerator, not just the whole quotient.
        assertEquals(Parser.parse("a + b"), MathLayout.exprAt(a.getParent()));
    }
}
