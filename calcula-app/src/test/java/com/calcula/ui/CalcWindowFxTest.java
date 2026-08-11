package com.calcula.ui;

import java.util.List;

import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
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
        public String eval(String input) throws CasException {
            if ("boom".equals(input)) {
                throw new CasException("bad syntax");
            }
            return "<" + input + ">";
        }

        @Override
        public String texForm(String input) {
            return "\\text{" + input + "}";
        }

        @Override
        public String mathmlForm(String input) {
            return "<mi>" + input + "</mi>";
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

        assertEquals(List.of("<2+3>"), FxTestSupport.callOnFx(window::stackContents));
        assertEquals(List.of("2+3", "  = <2+3>"), FxTestSupport.callOnFx(window::trailContents));
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
        assertTrue(FxTestSupport.callOnFx(window::trailContents).get(1).contains("bad syntax"));

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
        assertEquals(List.of("<a>", "<b>", "<c>"), FxTestSupport.callOnFx(window::stackContents));

        @SuppressWarnings("unchecked")
        ListView<String> stack =
                (ListView<String>) FxTestSupport.callOnFx(() -> window.getRoot().lookup(".stack-view"));
        assertEquals(3, FxTestSupport.callOnFx(() -> stack.getItems().size()));
        FxTestSupport.runOnFx(window::dispose);
    }
}
