package com.calcula.ui;

import java.util.List;

import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import com.calcula.key.KeyDispatcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("fx")
class CalcWindowFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    /** A stand-in engine, so the UI is tested without paying Symja's ~650 ms start-up. */
    private record StubEngine(String id, String version) implements CasEngine {
        @Override
        public Expr eval(Expr input) throws CasException {
            if (Exprs.isSymbol(input, "boom")) {
                throw new CasException("engine says no");
            }
            return input; // identity, so assertions read as the notation that went in
        }

        @Override
        public String texForm(Expr input) {
            return "\\text{tex}";
        }

        @Override
        public String mathmlForm(Expr input) {
            return "<mi>mathml</mi>";
        }
    }

    @Test
    void theWindowBuildsWithAllFourRegions() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());

        List<String> classes = FxTestSupport.callOnFx(() -> window.getRoot().lookupAll("*").stream()
                .flatMap(n -> n.getStyleClass().stream())
                .distinct()
                .toList());

        assertTrue(classes.contains("stack-view"), classes.toString());
        assertTrue(classes.contains("trail-view"), classes.toString());
        assertTrue(classes.contains("mode-line"), classes.toString());
        assertTrue(classes.contains("echo-area"), classes.toString());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void evaluatingPushesOntoTheStackAndRecordsBothSidesInTheTrail() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        window.setEngine(new StubEngine("stub", "1.0"));

        FxTestSupport.runOnFx(() -> window.submit("2+3"));
        FxTestSupport.waitFor(
                "the result to reach the stack",
                5000,
                () -> !window.stackContents().isEmpty());

        // Folded by the numeric evaluator without ever consulting the engine.
        assertEquals(List.of("5"), FxTestSupport.callOnFx(window::stackDisplay));
        assertEquals(List.of("2+3", "  = 5"), FxTestSupport.callOnFx(window::trailContents));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aFailedEvaluationLeavesTheStackAloneAndGivesTheInputBack() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        window.setEngine(new StubEngine("stub", "1.0"));

        FxTestSupport.runOnFx(() -> window.submit("boom"));
        FxTestSupport.waitFor(
                "the error to reach the trail",
                5000,
                () -> window.trailContents().size() >= 2);

        // Nothing pushed: a failed evaluation must not silently leave a bogus entry behind.
        assertTrue(FxTestSupport.callOnFx(window::stackContents).isEmpty());
        assertTrue(FxTestSupport.callOnFx(window::trailContents).get(1).contains("engine says no"));

        TextField input =
                (TextField) FxTestSupport.callOnFx(() -> window.getRoot().lookup(".echo-input"));
        assertEquals("boom", FxTestSupport.callOnFx(input::getText), "the bad input should be recoverable");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theModeLineReportsWhetherAnEngineWasFound() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        assertNotNull(FxTestSupport.callOnFx(() -> window.getRoot().lookup(".mode-line")));

        window.setEngine(new StubEngine("symja", "3.0.0"));
        FxTestSupport.waitFor(
                "the engine status to update",
                5000,
                () -> window.getRoot().lookupAll(".mode-item").stream()
                        .anyMatch(n -> n instanceof Label l && l.getText().contains("symja 3.0.0")));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void stackEntriesAreNumberedFromTheBottomUpLikeCalc() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        window.setEngine(new StubEngine("stub", "1.0"));

        for (String s : List.of("a", "b", "c")) {
            FxTestSupport.runOnFx(() -> window.submit(s));
            FxTestSupport.waitFor(
                    "entry " + s, 5000, () -> window.stackDisplay().contains(s));
        }
        FxTestSupport.waitFor(
                "three entries", 5000, () -> window.stackContents().size() == 3);

        // Backing order is bottom-to-top, so the LAST element is stack entry 1: — what Calc draws
        // nearest the input line.
        assertEquals(List.of("a", "b", "c"), FxTestSupport.callOnFx(window::stackDisplay));

        @SuppressWarnings("unchecked")
        ListView<Expr> stack =
                (ListView<Expr>) FxTestSupport.callOnFx(() -> window.getRoot().lookup(".stack-view"));
        assertEquals(3, FxTestSupport.callOnFx(() -> stack.getItems().size()));
        FxTestSupport.runOnFx(window::dispose);
    }

    // ---- the machine behind the window -------------------------------------------------------

    @Test
    void undoStepsBackThroughEntries() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        window.setEngine(new StubEngine("stub", "1.0"));

        FxTestSupport.runOnFx(() -> window.submit("1 + 1"));
        FxTestSupport.waitFor("the entry", 5000, () -> window.stackDisplay().equals(List.of("2")));

        FxTestSupport.runOnFx(() -> window.run("edit.undo"));
        FxTestSupport.waitFor("the undo", 5000, () -> window.stackContents().isEmpty());

        FxTestSupport.runOnFx(() -> window.run("edit.redo"));
        FxTestSupport.waitFor("the redo", 5000, () -> window.stackDisplay().equals(List.of("2")));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void chordsReachTheMachineThroughTheKeymap() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        window.setEngine(new StubEngine("stub", "1.0"));

        for (String value : List.of("1", "2")) {
            FxTestSupport.runOnFx(() -> window.submit(value));
            FxTestSupport.waitFor(
                    "entry " + value, 5000, () -> window.stackDisplay().contains(value));
        }

        FxTestSupport.runOnFx(() -> window.press("TAB"));
        FxTestSupport.waitFor("the swap", 5000, () -> window.stackDisplay().equals(List.of("2", "1")));

        FxTestSupport.runOnFx(() -> window.press("DEL"));
        FxTestSupport.waitFor("the drop", 5000, () -> window.stackDisplay().equals(List.of("2")));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aPrefixSequenceWaitsForItsSecondChord() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        window.setEngine(new StubEngine("stub", "1.0"));

        FxTestSupport.runOnFx(() -> window.submit("7"));
        FxTestSupport.waitFor("the entry", 5000, () -> window.stackDisplay().equals(List.of("7")));

        // C-x on its own must do nothing; C-x u is the undo.
        assertEquals(
                KeyDispatcher.Outcome.PENDING,
                FxTestSupport.callOnFx(() -> window.press("C-x")).outcome());
        assertEquals(List.of("7"), FxTestSupport.callOnFx(window::stackDisplay));

        FxTestSupport.runOnFx(() -> window.press("u"));
        FxTestSupport.waitFor("the undo", 5000, () -> window.stackContents().isEmpty());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theEntryModelCanBeSwitchedAtRuntime() throws Exception {
        // Which model is the default is still an open question, so the window must be able to run
        // either without being rebuilt.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        window.setEngine(new StubEngine("stub", "1.0"));

        assertEquals("algebraic", window.readerId());
        FxTestSupport.runOnFx(() -> window.run("input.toggleModel"));
        assertEquals("rpn", window.readerId());

        FxTestSupport.runOnFx(() -> window.submit("3 4 +"));
        FxTestSupport.waitFor(
                "the postfix sum", 5000, () -> window.stackDisplay().equals(List.of("7")));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void arithmeticNeverReachesTheEngine() throws Exception {
        // The numeric fold is the fast path AND the offline path; a plain sum must not pay a round
        // trip, and must still work with no engine at all.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        window.setEngine(new CasEngine() {
            @Override
            public String id() {
                return "explodes";
            }

            @Override
            public String version() {
                return "1.0";
            }

            @Override
            public Expr eval(Expr input) throws CasException {
                throw new CasException("the engine should not have been consulted");
            }

            @Override
            public String texForm(Expr input) throws CasException {
                throw new CasException("no");
            }

            @Override
            public String mathmlForm(Expr input) throws CasException {
                throw new CasException("no");
            }
        });

        FxTestSupport.runOnFx(() -> window.submit("1/2 + 1/3"));
        FxTestSupport.waitFor("the exact sum", 5000, () -> window.stackDisplay().equals(List.of("5/6")));
        FxTestSupport.runOnFx(window::dispose);
    }
}
