package com.calcula.ui;

import com.calcula.help.Example;
import com.calcula.help.Examples;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Picking an example. */
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
    void pickingAnExamplePutsItOnTheInputLineAndStopsThere() throws Exception {
        // Deliberately not run. An example that ran itself would demonstrate a gesture nobody can
        // repeat — the thing being taught is what to type — and it would deny the obvious next move,
        // which is to change a number before pressing Enter.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Example example = named("Exact arithmetic");
        FxTestSupport.runOnFx(() -> window.useExample(example));

        assertEquals(
                example.source(),
                FxTestSupport.callOnFx(() -> window.inputField().getText()));
        assertTrue(window.stackContents().isEmpty(), "the example ran itself");
        assertTrue(window.trailContents().isEmpty(), "the example ran itself");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theCaretLandsAtTheEndSoItCanBeEdited() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Example example = named("Multiply out");
        FxTestSupport.runOnFx(() -> window.useExample(example));
        assertEquals(example.source().length(), (int)
                FxTestSupport.callOnFx(() -> window.inputField().getCaretPosition()));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void andThenEnterRunsIt() throws Exception {
        // The whole point: what lands on the input line is exactly what the user would have typed,
        // so running it is their ordinary Enter and nothing special.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> window.useExample(named("Exact arithmetic")));
        FxTestSupport.runOnFx(() -> window.submit(window.inputField().getText()));
        FxTestSupport.waitFor("the result", 5000, () -> !window.stackContents().isEmpty());
        assertEquals(
                "1/2", com.calcula.parse.Formatter.format(window.stackContents().get(0)));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void everyExampleCanBePickedWithoutRaisingAnything() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        for (Example example : Examples.all()) {
            FxTestSupport.runOnFx(() -> window.useExample(example));
            assertEquals(
                    example.source(),
                    FxTestSupport.callOnFx(() -> window.inputField().getText()));
        }
        assertTrue(window.stackContents().isEmpty(), "picking examples ran one of them");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theSheetIsReachableAsACommand() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        assertTrue(FxTestSupport.callOnFx(() -> window.run("help.examples")), "no help.examples command");
        FxTestSupport.runOnFx(window::dispose);
    }
}
