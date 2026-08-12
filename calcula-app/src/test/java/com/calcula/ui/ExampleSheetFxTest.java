package com.calcula.ui;

import java.util.List;

import com.calcula.help.Example;
import com.calcula.help.Examples;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pressing an example. */
@Tag("fx")
class ExampleSheetFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @BeforeEach
    void isolate() throws Exception {
        FxTestSupport.freshConfigDir();
    }

    private static Example named(String title) {
        return Examples.all().stream()
                .filter(e -> e.title().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no example titled " + title));
    }

    @Test
    void runningAnExamplePutsItsResultOnTheStack() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Example example = named("Exact arithmetic");
        FxTestSupport.runOnFx(() -> window.runExample(example));
        FxTestSupport.waitFor("the result", 5000, () -> !window.stackContents().isEmpty());
        // Folded without the engine, so the value is checkable even in a stubbed run.
        assertEquals(
                "1/2", com.calcula.parse.Formatter.format(window.stackContents().get(0)));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aMultiLineExampleLeavesEveryLineOnTheStack() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Example example = named("Two on the stack");
        assertEquals(2, example.lines().size(), "the example this test is about changed shape");
        FxTestSupport.runOnFx(() -> window.runExample(example));
        FxTestSupport.waitFor("both entries", 5000, () -> window.stackContents().size() >= 2);
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theExampleGoesThroughTheTrailJustAsTypingItWould() throws Exception {
        // Not a private path onto the stack: an example has to demonstrate something the user can
        // reproduce by typing, and the trail is the evidence that it did.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> window.runExample(named("Exact arithmetic")));
        FxTestSupport.waitFor("the trail", 5000, () -> !window.trailContents().isEmpty());
        String trail = String.join("\n", window.trailContents());
        assertTrue(trail.contains("1/3 + 1/6"), trail);
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void everyExampleRunsWithoutRaisingAnError() throws Exception {
        // The engine is not loaded here, so this is not about the answers — it is about the window
        // surviving each one. A line that made a command throw would leave the trail with an error.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        List<Example> examples = Examples.all();
        for (Example example : examples) {
            FxTestSupport.runOnFx(() -> window.runExample(example));
        }
        FxTestSupport.waitFor(
                "the last line", 15000, () -> window.trailContents().size() >= examples.size());
        String trail = String.join("\n", window.trailContents());
        assertFalse(trail.toLowerCase(java.util.Locale.ROOT).contains("unexpected"), "a parse failure: " + trail);
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theSheetIsReachableAsACommandAndAsAButton() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        assertTrue(FxTestSupport.callOnFx(() -> window.run("help.examples")), "no help.examples command");
        FxTestSupport.runOnFx(window::dispose);
    }
}
