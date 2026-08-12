package com.calcula.ui;

import java.util.List;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rule &amp; Plate state language, where it reaches the scene graph.
 *
 * <p>These assert <b>style classes</b>, not colours. The colour lives in a theme sheet and is meant
 * to be swapped; what must not drift is which meaning gets which class. A class no test names is a
 * class that quietly stops being applied, and the symptom — a marker that never appears — looks
 * exactly like the feature working.
 */
@Tag("fx")
class StateLanguageFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    /** Identity engine, so what goes in is what lands on the stack. */
    private record StubEngine(String id, String version) implements CasEngine {
        @Override
        public Expr eval(Expr input) throws CasException {
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
        FxTestSupport.realize(window.getRoot());
        window.setEngine(new StubEngine("stub", "1.0"));
        return window;
    }

    /**
     * Lay out again, after the data has landed.
     *
     * <p>{@code realize()} applies CSS and lays out once, at construction, when both lists are
     * still empty — so a virtual flow has built no cells and every {@code .list-cell} selector
     * finds nothing. Without this the assertions below fail in a way that looks exactly like the
     * style class never being applied.
     */
    private static void relayout(CalcWindow window) throws Exception {
        FxTestSupport.runOnFx(() -> {
            window.getRoot().applyCss();
            window.getRoot().layout();
        });
    }

    private static List<String> classesOf(CalcWindow window, String selector) throws Exception {
        return FxTestSupport.callOnFx(() -> window.getRoot().lookupAll(selector).stream()
                .flatMap(n -> n.getStyleClass().stream())
                .distinct()
                .toList());
    }

    @Test
    void everyStackRowCarriesAGutterRailEvenWhenItHasNothingToSay() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit("1/2 + 1/3"));
        FxTestSupport.waitFor(
                "the exact result", 5000, () -> !window.stackContents().isEmpty());

        // The rail is always present and usually transparent. If it were added only when a marker
        // applies, a value turning inexact would shift the text beside it by three pixels.
        relayout(window);
        assertTrue(classesOf(window, ".stack-gutter").contains("stack-gutter"));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void anExactValueWearsNoInexactMarker() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit("1/2 + 1/3"));
        FxTestSupport.waitFor(
                "the exact result", 5000, () -> !window.stackContents().isEmpty());

        relayout(window);
        assertEquals(List.of("5/6"), FxTestSupport.callOnFx(window::stackDisplay));
        assertFalse(classesOf(window, ".stack-gutter").contains("inexact"), "5/6 is exact and must stay unmarked");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void anInexactValueMarksItsGutterAndItsIndex() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit("1.5"));
        FxTestSupport.waitFor("the float", 5000, () -> !window.stackContents().isEmpty());

        relayout(window);
        assertTrue(Exprs.containsInexact(
                FxTestSupport.callOnFx(() -> window.stackContents().get(0))));
        assertTrue(classesOf(window, ".stack-gutter").contains("inexact"), "the rail should carry the marker");
        assertTrue(classesOf(window, ".stack-index").contains("inexact"), "so should the index");
        FxTestSupport.runOnFx(window::dispose);
    }

    /**
     * The case that separates {@code containsInexact} from {@code !isExact}: a symbolic result is a
     * Call, and the shallow predicate answers false for every Call. Getting this wrong paints the
     * marker on essentially everything the CAS returns.
     */
    @Test
    void aSymbolicResultIsNotMarkedApproximate() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit("x + 1"));
        FxTestSupport.waitFor(
                "the symbolic result", 5000, () -> !window.stackContents().isEmpty());

        relayout(window);
        assertFalse(
                classesOf(window, ".stack-gutter").contains("inexact"),
                "x + 1 carries no numeric error and must not wear the marker");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theTrailColoursInputAndResultDifferently() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit("2+3"));
        FxTestSupport.waitFor(
                "both trail lines", 5000, () -> window.trailContents().size() >= 2);

        relayout(window);
        List<String> classes = classesOf(window, ".trail-view .list-cell");
        assertTrue(classes.contains("trail-input"), classes.toString());
        assertTrue(classes.contains("trail-result"), classes.toString());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aHeldPrefixMarksThePrompt() throws Exception {
        CalcWindow window = open();
        TextField field =
                (TextField) FxTestSupport.callOnFx(() -> window.getRoot().lookup(".echo-input"));
        Node prompt = FxTestSupport.callOnFx(() -> window.getRoot().lookup(".echo-prompt"));

        // A real event, not the press() seam: the prompt is updated in onKey, which press() skips,
        // so driving the seam here would assert nothing about the wiring that actually runs.
        FxTestSupport.runOnFx(() -> field.fireEvent(keyPress(KeyCode.X, true)));

        assertEquals("C-x-", FxTestSupport.callOnFx(() -> ((Label) prompt).getText()));
        assertTrue(
                FxTestSupport.callOnFx(() -> prompt.getStyleClass().contains("pending")),
                "a half-entered chord is a value that has not settled, and says so");

        // Escape is an unbound chord, so it resolves the sequence to nothing and the prompt
        // returns to ready. A stale pending class would leave the prompt amber for the rest of the
        // session, which reads as the calculator being permanently mid-thought.
        FxTestSupport.runOnFx(() -> field.fireEvent(keyPress(KeyCode.ESCAPE, false)));
        assertEquals("\u203a", FxTestSupport.callOnFx(() -> ((Label) prompt).getText()));
        assertFalse(FxTestSupport.callOnFx(() -> prompt.getStyleClass().contains("pending")));
        FxTestSupport.runOnFx(window::dispose);
    }

    /**
     * The other half of amber: a value the engine declined to work out.
     *
     * <p>{@code Hold(Fibonacci(100))} reached the stack looking exactly like an answer — same ink,
     * same weight, transparent rail — sitting beside real ones. The rail's meaning already covered it
     * ("not exact, <em>or not finished</em>"); nothing was asking the second question.
     */
    @Test
    void aValueTheEngineWouldNotEvaluateIsMarkedAndSaysWhy() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit("Hold(Fibonacci(100))"));
        FxTestSupport.waitFor(
                "the held call", 5000, () -> !window.stackContents().isEmpty());

        relayout(window);
        assertTrue(classesOf(window, ".stack-gutter").contains("inexact"), "a held result is not a final answer");

        // And it can say what it is complaining about — a rail that cannot be interrogated is a
        // puzzle rather than a signal.
        assertEquals(
                "Fibonacci",
                RowMarker.heldName(
                        FxTestSupport.callOnFx(() -> window.stackContents().get(0))));
        FxTestSupport.runOnFx(window::dispose);
    }

    private static KeyEvent keyPress(KeyCode code, boolean control) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, control, false, false);
    }
}
