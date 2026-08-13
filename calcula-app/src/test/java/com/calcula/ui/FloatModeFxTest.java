package com.calcula.ui;

import java.util.List;

import com.calcula.cas.CasEngine;
import com.calcula.expr.Expr;
import com.calcula.key.KeyDispatcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The float display mode as a mode: reachable, visible in the mode line, and undoable. */
@Tag("fx")
class FloatModeFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    private record StubEngine(String id, String version) implements CasEngine {
        @Override
        public Expr eval(Expr input) {
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

    private static CalcWindow window() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        window.setEngine(new StubEngine("stub", "1.0"));
        return window;
    }

    private static void settle(String what, java.util.concurrent.Callable<Boolean> done) throws Exception {
        FxTestSupport.waitFor(what, 5000, done);
    }

    @Test
    void theDigitsComeFromTheInputLineAndTheModeLineSaysSo() throws Exception {
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> {
            window.type("4");
            window.run("mode.floatFixed");
        });
        settle("the mode line to report it", () -> window.modeLine().contains("fix 4"));
        assertEquals("", FxTestSupport.callOnFx(window::typed), "the count should be consumed");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void switchingStyleKeepsTheDigitsAlreadyAskedFor() throws Exception {
        // Otherwise choosing scientific after fixed silently resets how many places you wanted, and
        // the second keystroke undoes the first.
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> {
            window.type("3");
            window.run("mode.floatFixed");
        });
        settle("fixed", () -> window.modeLine().contains("fix 3"));

        FxTestSupport.runOnFx(() -> window.run("mode.floatScientific"));
        settle("scientific, still at three", () -> window.modeLine().contains("sci 3"));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void normalSaysNothingAtAllInTheModeLine() throws Exception {
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> {
            window.type("2");
            window.run("mode.floatEngineering");
        });
        settle("engineering", () -> window.modeLine().contains("eng 2"));

        FxTestSupport.runOnFx(() -> window.run("mode.floatNormal"));
        settle("back to the default", () -> !window.modeLine().contains("eng"));
        assertTrue(FxTestSupport.callOnFx(window::modeLine).contains("prec"), "the rest of the line is still there");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aDigitCountThatIsNotANumberSaysSoAndChangesNothing() throws Exception {
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> {
            window.type("lots");
            window.run("mode.floatFixed");
        });
        settle("the refusal", () -> window.trailContents().stream().anyMatch(l -> l.contains("number of digits")));
        assertTrue(!FxTestSupport.callOnFx(window::modeLine).contains("fix"), "the mode should be untouched");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aChangeOfDisplayUndoesLikeEveryOtherMode() throws Exception {
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> {
            window.type("2");
            window.run("mode.floatFixed");
        });
        settle("fixed", () -> window.modeLine().contains("fix 2"));

        FxTestSupport.runOnFx(() -> window.run("edit.undo"));
        settle("and back", () -> !window.modeLine().contains("fix"));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theChordsReachTheCommands() throws Exception {
        CalcWindow window = window();
        for (String[] pair :
                List.of(new String[] {"n", "mode.floatNormal"}, new String[] {"x", "mode.floatFixed"}, new String[] {
                    "e", "mode.floatScientific"
                })) {
            KeyDispatcher.Result prefix = FxTestSupport.callOnFx(() -> window.press("M-m"));
            assertEquals(KeyDispatcher.Outcome.PENDING, prefix.outcome(), "M-m should be waiting for the rest");

            KeyDispatcher.Result ran = FxTestSupport.callOnFx(() -> window.press(pair[0]));
            assertEquals(KeyDispatcher.Outcome.RAN, ran.outcome(), pair[1]);
            assertEquals(pair[1], ran.commandId());
        }
        FxTestSupport.runOnFx(window::dispose);
    }
}
