package com.calcula.ui;

import java.util.List;

import com.calcula.cas.CasEngine;
import com.calcula.expr.Expr;
import com.calcula.key.KeyDispatcher;
import com.calcula.machine.TrailEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The four small things that separate a stack you operate from a stack you look at. */
@Tag("fx")
class StackErgonomicsFxTest {

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
        FxTestSupport.realize(window.getRoot());
        return window;
    }

    private static void settle(String what, java.util.concurrent.Callable<Boolean> done) throws Exception {
        FxTestSupport.waitFor(what, 5000, done);
    }

    private static void submit(CalcWindow window, String text) throws Exception {
        int was = FxTestSupport.callOnFx(() -> window.trailContents().size());
        FxTestSupport.runOnFx(() -> window.submit(text));
        settle("the line to be recorded", () -> window.trailContents().size() > was);
    }

    // ---- last arguments --------------------------------------------------------------------

    @Test
    void theArgumentsComeBackAndTheAnswerStays() throws Exception {
        // Undo restores the state; this restores the inputs. After a mistyped operator the second is
        // what you wanted.
        CalcWindow window = window();
        submit(window, "2 + 3");
        settle("the sum", () -> window.stackDisplay().equals(List.of("5")));

        FxTestSupport.runOnFx(() -> window.run("edit.lastArgs"));
        settle("the arguments", () -> window.stackDisplay().equals(List.of("5", "2", "3")));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aValueWithNoHistoryToPutBackSaysSo() throws Exception {
        CalcWindow window = window();
        submit(window, "42");

        FxTestSupport.runOnFx(() -> window.run("edit.lastArgs"));
        settle("the refusal", () -> window.trailContents().stream().anyMatch(l -> l.contains("nothing is recorded")));
        assertEquals(List.of("42"), FxTestSupport.callOnFx(window::stackDisplay));
        FxTestSupport.runOnFx(window::dispose);
    }

    // ---- editing an entry ------------------------------------------------------------------

    @Test
    void editingTakesTheValueOntoTheInputLineAndSubmittingPutsItBack() throws Exception {
        CalcWindow window = window();
        submit(window, "x + 1");

        FxTestSupport.runOnFx(() -> window.run("edit.editEntry"));
        settle("the value to reach the line", () -> window.typed().equals("x + 1"));
        assertTrue(FxTestSupport.callOnFx(window::stackContents).isEmpty(), "it is on the line, not in two places");

        FxTestSupport.runOnFx(() -> window.submit("x + 2"));
        settle("the edit", () -> window.stackDisplay().equals(List.of("x + 2")));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void anAbandonedEditIsUndoable() throws Exception {
        // Taking the value off the stack is the whole design — so the way back has to be the ordinary
        // one, and it is.
        CalcWindow window = window();
        submit(window, "7");

        FxTestSupport.runOnFx(() -> window.run("edit.editEntry"));
        settle("the value to reach the line", () -> window.typed().equals("7"));

        FxTestSupport.runOnFx(() -> window.run("edit.undo"));
        settle("the value to come back", () -> window.stackDisplay().equals(List.of("7")));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void editingAnEmptyStackSaysSoRatherThanClearingTheInputLine() throws Exception {
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> {
            window.type("half typed");
            window.run("edit.editEntry");
        });
        settle("the refusal", () -> window.trailContents().stream().anyMatch(l -> l.contains("no stack entry")));
        assertEquals("half typed", FxTestSupport.callOnFx(window::typed), "and what was being typed survives");
        FxTestSupport.runOnFx(window::dispose);
    }

    // ---- the trail -------------------------------------------------------------------------

    @Test
    void aTrailLineGoesBackOnTheStack() throws Exception {
        CalcWindow window = window();
        submit(window, "1/3 + 1/6");
        settle("the answer", () -> window.stackDisplay().equals(List.of("1/2")));
        FxTestSupport.runOnFx(() -> window.run("stack.drop"));
        settle("an empty stack", () -> window.stackContents().isEmpty());

        // The input line that produced it, taken back off the trail rather than retyped.
        FxTestSupport.runOnFx(() -> window.selectTrailLine(0));
        FxTestSupport.runOnFx(() -> window.run("trail.yank"));
        settle("the value", () -> window.stackDisplay().equals(List.of("1/2")));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aNoteIsNotAValueAndIsNotOffered() throws Exception {
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> window.run("mode.degrees"));
        settle("the note", () -> window.trailContents().stream().anyMatch(l -> l.contains("deg")));

        assertFalse(CalcWindow.isYankable(new TrailEntry(TrailEntry.Kind.NOTE, "rad  prec 12")));
        assertFalse(CalcWindow.isYankable(new TrailEntry(TrailEntry.Kind.ERROR, "no such command")));
        assertTrue(CalcWindow.isYankable(new TrailEntry(TrailEntry.Kind.RESULT, "1/2")));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void searchingTheTrailShowsOnlyWhatMatches() throws Exception {
        CalcWindow window = window();
        submit(window, "111");
        submit(window, "222");
        settle("both", () -> window.trailContents().size() >= 4);

        FxTestSupport.runOnFx(() -> window.typeInTrailFilter("222"));
        settle("the filter", () -> window.trailContents().stream().allMatch(l -> l.contains("222")));
        assertFalse(FxTestSupport.callOnFx(window::trailContents).isEmpty());

        // And a filter has to survive the next result arriving, or it is one you cannot work under.
        // Waited on the STACK, not the trail: trailContents is what is shown, and the whole point of
        // the filter is that a line which does not match never appears there.
        FxTestSupport.runOnFx(() -> window.submit("333"));
        settle("the value to land", () -> window.stackDisplay().contains("333"));
        assertTrue(
                FxTestSupport.callOnFx(window::trailContents).stream().allMatch(l -> l.contains("222")),
                "the filter should still be applied after a publish");

        FxTestSupport.runOnFx(() -> window.typeInTrailFilter(""));
        settle("everything back", () -> window.trailContents().size() >= 6);
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theChordsReachTheCommands() throws Exception {
        CalcWindow window = window();
        assertEquals(
                "edit.lastArgs",
                FxTestSupport.callOnFx(() -> window.press("M-RET")).commandId());
        assertEquals(
                "edit.editEntry",
                FxTestSupport.callOnFx(() -> window.press("M-e")).commandId());

        for (String[] pair : List.of(new String[] {"y", "trail.yank"}, new String[] {"s", "trail.search"})) {
            KeyDispatcher.Result prefix = FxTestSupport.callOnFx(() -> window.press("M-t"));
            assertEquals(KeyDispatcher.Outcome.PENDING, prefix.outcome(), "M-t should be waiting for the rest");
            assertEquals(
                    pair[1], FxTestSupport.callOnFx(() -> window.press(pair[0])).commandId());
        }
        FxTestSupport.runOnFx(window::dispose);
    }
}
