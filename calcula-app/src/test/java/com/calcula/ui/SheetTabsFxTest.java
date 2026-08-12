package com.calcula.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.calcula.doc.Sheet;
import com.calcula.doc.SheetStore;
import com.calcula.machine.Modes;
import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Several sheets at once. */
@Tag("fx")
class SheetTabsFxTest {

    @TempDir
    Path dir;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @BeforeEach
    void isolate() throws Exception {
        FxTestSupport.freshConfigDir();
    }

    private static String top(CalcWindow window) {
        List<com.calcula.expr.Expr> stack = window.stackContents();
        return stack.isEmpty() ? "" : Formatter.format(stack.get(stack.size() - 1));
    }

    private static void settle(CalcWindow window, String expected) throws Exception {
        FxTestSupport.waitFor("'" + expected + "' on the stack", 5000, () -> expected.equals(top(window)));
    }

    @Test
    void eachSheetKeepsItsOwnStack() throws Exception {
        // The property tabs exist for. A shared machine would make both tabs the same sheet with two
        // labels on it.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> window.submit("111"));
        settle(window, "111");

        FxTestSupport.runOnFx(window::newSheet);
        FxTestSupport.waitFor(
                "an empty second sheet", 5000, () -> window.stackContents().isEmpty());
        FxTestSupport.runOnFx(() -> window.submit("222"));
        settle(window, "222");

        FxTestSupport.runOnFx(() -> window.selectSheet(0));
        settle(window, "111");
        FxTestSupport.runOnFx(() -> window.selectSheet(1));
        settle(window, "222");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void undoActsOnTheSheetYouAreLookingAt() throws Exception {
        // A shared history would make the two documents' pasts a single interleaved one, so undo in
        // the second sheet would step back through something that happened in the first.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> window.submit("111"));
        settle(window, "111");
        FxTestSupport.runOnFx(window::newSheet);
        FxTestSupport.waitFor(
                "the new sheet", 5000, () -> window.stackContents().isEmpty());
        FxTestSupport.runOnFx(() -> window.submit("222"));
        settle(window, "222");

        FxTestSupport.runOnFx(() -> window.run("edit.undo"));
        FxTestSupport.waitFor("the undo", 5000, () -> window.stackContents().isEmpty());

        FxTestSupport.runOnFx(() -> window.selectSheet(0));
        settle(window, "111"); // untouched by the other sheet's undo
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aNewSheetNeverAsksAboutTheOneYouWereUsing() throws Exception {
        // The real gain from tabs: New stops being a question about work already on screen. If this
        // ever prompts, the test hangs on a modal — which is the failure being guarded against.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> window.submit("1 + 1"));
        settle(window, "2");
        FxTestSupport.runOnFx(window::newSheet);
        assertEquals(2, window.sheetCount());
        assertTrue(window.stackContents().isEmpty());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void openingIntoAnUntouchedSheetUsesItRatherThanAddingATab() throws Exception {
        Path file = dir.resolve("one.calc");
        SheetStore.write(file, new Sheet(List.of(Parser.parse("42")), Map.of(), Modes.DEFAULTS, List.of()));

        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> window.openSheet(file));
        settle(window, "42");
        assertEquals(1, window.sheetCount(), "an empty sheet was left behind");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void openingOverWorkInProgressOpensAnotherSheetInstead() throws Exception {
        Path file = dir.resolve("one.calc");
        SheetStore.write(file, new Sheet(List.of(Parser.parse("42")), Map.of(), Modes.DEFAULTS, List.of()));

        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> window.submit("999"));
        settle(window, "999");
        FxTestSupport.runOnFx(() -> window.openSheet(file));
        settle(window, "42");
        assertEquals(2, window.sheetCount());

        FxTestSupport.runOnFx(() -> window.selectSheet(0));
        settle(window, "999"); // still there
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void closingTheLastSheetLeavesAnEmptyOneRatherThanAnEmptyWindow() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(window::closeSheet);
        assertEquals(1, window.sheetCount());
        assertNotNull(window.title());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void cyclingWraps() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> {
            window.newSheet();
            window.newSheet();
        });
        assertEquals(3, window.sheetCount());
        FxTestSupport.runOnFx(() -> window.selectSheet(0));
        FxTestSupport.runOnFx(() -> window.cycleSheet(-1));
        // Wrapped to the last rather than stopping at the first, so both commands reach every sheet.
        FxTestSupport.runOnFx(() -> window.submit("last"));
        FxTestSupport.waitFor(
                "the third sheet", 5000, () -> !window.stackContents().isEmpty());
        FxTestSupport.runOnFx(() -> window.selectSheet(0));
        FxTestSupport.waitFor(
                "back at the first", 5000, () -> window.stackContents().isEmpty());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theStripAppearsOnlyWhenThereIsAChoiceToMake() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        assertFalse(window.tabStrip().isShowing(), "a strip for a single sheet is chrome for its own sake");
        FxTestSupport.runOnFx(window::newSheet);
        assertTrue(window.tabStrip().isShowing());
        assertEquals(List.of("Untitled", "Untitled"), window.tabStrip().titles());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aTabShowsItsFileNameAndWhetherItIsSaved() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(window::newSheet);
        FxTestSupport.runOnFx(() -> window.submit("1"));
        FxTestSupport.waitFor("the entry", 5000, () -> !window.stackContents().isEmpty());
        assertTrue(
                window.tabStrip().titles().get(1).startsWith("•"),
                window.tabStrip().titles().toString());

        Path file = dir.resolve("work.calc");
        FxTestSupport.runOnFx(() -> window.saveTo(file));
        FxTestSupport.waitFor("the save", 5000, () -> !window.isDirty());
        assertEquals("work", window.tabStrip().titles().get(1));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void workFinishingInABackgroundSheetDoesNotPaintOverTheVisibleOne() throws Exception {
        // The race the document capture exists for: an operation queued against one sheet must run
        // against THAT sheet's machine and must not publish into a window showing another.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> {
            window.submit("111"); // queued against sheet 0
            window.newSheet(); // switch before it has necessarily finished
            window.submit("222");
        });
        settle(window, "222");
        assertEquals(1, window.stackContents().size(), "the first sheet's value leaked into the second");

        FxTestSupport.runOnFx(() -> window.selectSheet(0));
        settle(window, "111"); // it did land, in its own sheet
        FxTestSupport.runOnFx(window::dispose);
    }
}
