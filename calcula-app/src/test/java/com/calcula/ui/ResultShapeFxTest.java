package com.calcula.ui;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Region;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A factorisation, set as one.
 *
 * <p>The value {@code [[2, 2], [3, 1]]} is drawn in matrix brackets when somebody typed it and as
 * {@code 2²·3} when {@code FactorInteger} produced it. Same value, different reading, and the only
 * thing that tells them apart is where it came from.
 */
@Tag("fx")
class ResultShapeFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @BeforeEach
    void isolate() throws Exception {
        FxTestSupport.freshConfigDir();
    }

    /** Factors 12 the way the real engine does; everything else passes through. */
    private record FactorStub(String id, String version) implements CasEngine {
        @Override
        public Expr eval(Expr input) throws CasException {
            if (input instanceof Expr.Call c && "FactorInteger".equals(c.head())) {
                return Exprs.list(Exprs.list(Exprs.of(2), Exprs.of(2)), Exprs.list(Exprs.of(3), Exprs.of(1)));
            }
            return input;
        }

        @Override
        public String texForm(Expr input) {
            return "";
        }

        @Override
        public String mathmlForm(Expr input) {
            return "";
        }
    }

    private static CalcWindow open() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realizeThemed(window.getRoot(), Themes.SLAB);
        window.setEngine(new FactorStub("stub", "1.0"));
        return window;
    }

    private static void collect(Node n, List<Node> into) {
        into.add(n);
        if (n instanceof Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                collect(c, into);
            }
        }
    }

    /** The top row's rendered content, and whether it used the matrix grid. */
    private static Region topRow(CalcWindow window) throws Exception {
        return FxTestSupport.callOnFx(() -> {
            window.getRoot().applyCss();
            window.getRoot().layout();
            for (Node n : window.getRoot().lookupAll(".stack-view .list-cell")) {
                if (n instanceof ListCell<?> cell && cell.getGraphic() instanceof Region row) {
                    return (Region) row.lookup(".stack-value");
                }
            }
            return null;
        });
    }

    /**
     * By class name, because MatrixNode is package-private and widening production visibility so a
     * test can name a type is a worse trade than a string.
     */
    private static boolean isMatrix(Region content) throws Exception {
        return FxTestSupport.callOnFx(() -> {
            List<Node> all = new ArrayList<>();
            collect(content, all);
            return all.stream().anyMatch(n -> "MatrixNode".equals(n.getClass().getSimpleName()));
        });
    }

    @Test
    void aFactorisationIsNotSetAsAMatrix() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit("FactorInteger(12)"));
        FxTestSupport.waitFor("the factors", 5000, () -> !window.stackContents().isEmpty());

        Region content = topRow(window);
        assertNotNull(content);
        assertFalse(isMatrix(content), "a factorisation is a product, not linear algebra");
        FxTestSupport.runOnFx(window::dispose);
    }

    /** The same value, typed rather than factored, keeps its brackets. Nothing is guessed from shape. */
    @Test
    void theSameValueTypedByHandIsStillAMatrix() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit("[[2, 2], [3, 1]]"));
        FxTestSupport.waitFor("the matrix", 5000, () -> !window.stackContents().isEmpty());

        assertTrue(isMatrix(topRow(window)), "typed by hand, so it is what it looks like");
        FxTestSupport.runOnFx(window::dispose);
    }

    /**
     * The reading is not the value, so nothing inside it may be addressable — a click resolving to a
     * path in a tree that is not on the stack would hand a transform an address into an expression the
     * user cannot see.
     */
    @Test
    void nothingInsideAReadingCanBeSelected() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit("FactorInteger(12)"));
        FxTestSupport.waitFor("the factors", 5000, () -> !window.stackContents().isEmpty());

        Region content = topRow(window);
        boolean anyAddressable = FxTestSupport.callOnFx(() -> {
            List<Node> all = new ArrayList<>();
            collect(content, all);
            return all.stream().anyMatch(n -> com.calcula.ui.math.MathLayout.selectionAt(n) != null);
        });
        assertFalse(anyAddressable, "a reading is not the value and must not pretend to be");
        FxTestSupport.runOnFx(window::dispose);
    }

    /** The value on the stack is untouched — only the drawing changed. */
    @Test
    void theValueItselfIsUnchanged() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit("FactorInteger(12)"));
        FxTestSupport.waitFor("the factors", 5000, () -> !window.stackContents().isEmpty());

        assertTrue(
                window.stackDisplay().get(0).contains("[[2, 2], [3, 1]]"),
                "the stack still holds the list: " + window.stackDisplay());
        assertNull(ResultShape.reading(Exprs.of(1), null), "and nothing else was reshaped");
        FxTestSupport.runOnFx(window::dispose);
    }
}
