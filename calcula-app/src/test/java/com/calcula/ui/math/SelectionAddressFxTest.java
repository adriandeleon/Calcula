package com.calcula.ui.math;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;

import com.calcula.expr.Expr;
import com.calcula.expr.ExprPath;
import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import com.calcula.ui.FxTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That an address in a rendering resolves to the thing drawn there.
 *
 * <p>This is the property a rewrite depends on, and the one that is easy to get quietly wrong: the
 * rendering does not mirror the tree — a fraction can be synthesised from {@code Times}, a radical from
 * {@code Power(x, 1/2)}, a minus lifted out of a coefficient — so a node can show a subterm that is at
 * no address at all. A mismatch would not throw; it would rewrite the wrong part of someone's answer.
 *
 * <p>So rather than checking a few cases by hand, this walks every node of a rendered formula and
 * asserts the invariant everywhere it claims to hold.
 */
@Tag("fx")
class SelectionAddressFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    private static List<Node> everyNode(Node root) {
        List<Node> all = new ArrayList<>();
        all.add(root);
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                all.addAll(everyNode(child));
            }
        }
        return all;
    }

    /** Render, then check every node that claims an address actually has the right one. */
    private static void checkEveryAddress(String source) throws Exception {
        Expr root = Parser.parse(source);
        Region rendered = FxTestSupport.callOnFx(() -> {
            Region r = MathLayout.render(root, MathStyle.of(17));
            new Scene(r);
            r.applyCss();
            r.layout();
            return r;
        });

        int addressed = 0;
        for (Node node : FxTestSupport.callOnFx(() -> everyNode(rendered))) {
            MathLayout.Selection selection = MathLayout.selectionAt(node);
            if (selection == null) {
                continue;
            }
            Expr atAddress = ExprPath.at(root, selection.path());
            assertNotNull(atAddress, source + ": path " + selection.path() + " addresses nothing");
            assertEquals(
                    Formatter.format(selection.expr()),
                    Formatter.format(atAddress),
                    source + ": node shows " + Formatter.format(selection.expr()) + " but its address "
                            + selection.path() + " holds " + Formatter.format(atAddress));
            addressed++;
        }
        assertTrue(addressed > 0, source + ": nothing was addressable at all");
    }

    @Test
    void addressesAgreeWithWhatIsDrawn() throws Exception {
        for (String source : List.of(
                "x + 1",
                "x + x",
                "a - b",
                "integrate(x*sin(x), x)",
                "-x*cos(x) + sin(x)",
                "sqrt(1 - x^2)",
                "x^2 + 2*x + 1",
                "1/x",
                "(x+1)/(x^2-3)",
                "[[1, 2], [3, 4]]",
                "f(a, g(b), 2)",
                "sin(x)^2",
                "2^100",
                "x = 4",
                "1/2 + 1/3")) {
            checkEveryAddress(source);
        }
    }

    @Test
    void theRootIsAlwaysAddressable() throws Exception {
        Expr root = Parser.parse("integrate(x*sin(x), x)");
        Region rendered = FxTestSupport.callOnFx(() -> MathLayout.render(root, MathStyle.of(17)));
        MathLayout.Selection selection = FxTestSupport.callOnFx(() -> MathLayout.selectionAt(rendered));
        assertEquals(ExprPath.ROOT, selection.path());
        assertEquals(root, selection.expr());
    }

    @Test
    void aFunctionArgumentIsReachableAndCorrect() throws Exception {
        // The headline case: the sin(x) inside an integral, at [0, 1].
        Expr root = Parser.parse("integrate(x*sin(x), x)");
        assertEquals("sin(x)", Formatter.format(ExprPath.at(root, List.of(0, 1))));
    }

    @Test
    void aSynthesisedNodeReportsNoAddressRatherThanAWrongOne() throws Exception {
        // In a - b the second term is drawn as `b` after a lifted minus, but argument 1 holds
        // Times(-1, b). Claiming an address there would rewrite the sign away.
        Expr root = Parser.parse("a - b");
        Region rendered = FxTestSupport.callOnFx(() -> {
            Region r = MathLayout.render(root, MathStyle.of(17));
            new Scene(r);
            r.applyCss();
            r.layout();
            return r;
        });
        for (Node node : FxTestSupport.callOnFx(() -> everyNode(rendered))) {
            MathLayout.Selection selection = MathLayout.selectionAt(node);
            if (selection != null && Formatter.format(selection.expr()).equals("b")) {
                assertEquals(
                        "b",
                        Formatter.format(ExprPath.at(root, selection.path())),
                        "if it claims an address for b, that address had better hold b");
            }
        }
    }

    @Test
    void aNodeOutsideAnyFormulaHasNoSelection() throws Exception {
        assertNull(FxTestSupport.callOnFx(() -> MathLayout.selectionAt(new javafx.scene.text.Text("loose"))));
    }
}
