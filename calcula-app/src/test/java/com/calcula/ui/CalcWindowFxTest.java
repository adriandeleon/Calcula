package com.calcula.ui;

import java.util.List;

import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
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

        // Typed as 2+3, stored as a tree, displayed through the formatter.
        assertEquals(List.of("2 + 3"), FxTestSupport.callOnFx(window::stackDisplay));
        assertEquals(List.of("2+3", "  = 2 + 3"), FxTestSupport.callOnFx(window::trailContents));
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

        // Nothing pushed: a syntax error must not silently leave a bogus entry behind.
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
}
