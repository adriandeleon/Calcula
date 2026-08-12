package com.calcula.ui;

import java.nio.file.Files;
import java.nio.file.Path;

import com.calcula.doc.Sheet;
import com.calcula.doc.SheetStore;
import com.calcula.parse.Formatter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Saving and opening, through the window. */
@Tag("fx")
class SheetFilesFxTest {

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

    private static void settle(CalcWindow window, int entries) throws Exception {
        FxTestSupport.waitFor("the stack", 5000, () -> window.stackContents().size() >= entries);
    }

    @Test
    void aSavedSheetComesBack() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> {
            window.submit("x^2 + 1");
            window.submit("1/3 + 1/6");
        });
        settle(window, 2);

        Path file = dir.resolve("work.calc");
        FxTestSupport.runOnFx(() -> window.saveTo(file));
        FxTestSupport.waitFor("the file", 5000, () -> Files.exists(file));

        CalcWindow reopened = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> reopened.openSheet(file));
        settle(reopened, 2);
        assertEquals(
                "1/2", Formatter.format(reopened.stackContents().get(1)), "the top of the stack did not come back");
        FxTestSupport.runOnFx(window::dispose);
        FxTestSupport.runOnFx(reopened::dispose);
    }

    @Test
    void savingClearsTheModifiedMarkAndTheTitleFollowsIt() throws Exception {
        // The mark is set by the publish that follows the save, so getting this right needed the
        // clean to travel with the work rather than run beside it.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> window.submit("2 + 2"));
        settle(window, 1);
        assertTrue(window.isDirty(), "typing did not mark the sheet modified");
        assertTrue(window.title().startsWith("•"), window.title());

        Path file = dir.resolve("work.calc");
        FxTestSupport.runOnFx(() -> window.saveTo(file));
        FxTestSupport.waitFor("the save", 5000, () -> !window.isDirty());
        assertFalse(window.title().startsWith("•"), window.title());
        assertTrue(window.title().startsWith("work"), window.title());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void openingASheetDoesNotImmediatelyMarkItModified() throws Exception {
        // The bug this pins: the load's own publish marked the file dirty, so a sheet was "unsaved"
        // the instant it was opened and closing it prompted about work nobody had done.
        Path file = dir.resolve("saved.calc");
        SheetStore.write(
                file,
                new Sheet(
                        java.util.List.of(com.calcula.parse.Parser.parse("x + 1")),
                        java.util.Map.of(),
                        com.calcula.machine.Modes.DEFAULTS,
                        java.util.List.of()));

        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> window.openSheet(file));
        settle(window, 1);
        assertFalse(window.isDirty(), "an untouched sheet reported itself modified");
        assertEquals(file, window.sheetFile());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void openingReplacesRatherThanAppends() throws Exception {
        Path file = dir.resolve("one.calc");
        SheetStore.write(
                file,
                new Sheet(
                        java.util.List.of(com.calcula.parse.Parser.parse("99")),
                        java.util.Map.of(),
                        com.calcula.machine.Modes.DEFAULTS,
                        java.util.List.of()));

        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> {
            window.submit("1");
            window.submit("2");
            window.submit("3");
        });
        settle(window, 3);
        FxTestSupport.runOnFx(() -> window.openSheet(file));
        FxTestSupport.waitFor("the load", 5000, () -> window.stackContents().size() == 1);
        assertEquals("99", Formatter.format(window.stackContents().get(0)));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void undoAfterOpeningDoesNotWalkBackIntoTheSheetThatWasThere() throws Exception {
        // A stack from one document appearing inside another is indistinguishable from corruption,
        // so the history is dropped at the boundary.
        Path file = dir.resolve("other.calc");
        SheetStore.write(
                file,
                new Sheet(
                        java.util.List.of(com.calcula.parse.Parser.parse("7")),
                        java.util.Map.of(),
                        com.calcula.machine.Modes.DEFAULTS,
                        java.util.List.of()));

        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> window.submit("1234"));
        settle(window, 1);
        FxTestSupport.runOnFx(() -> window.openSheet(file));
        FxTestSupport.waitFor("the load", 5000, () -> window.stackContents().size() == 1);

        FxTestSupport.runOnFx(() -> window.run("edit.undo"));
        FxTestSupport.waitFor("the undo", 5000, () -> true);
        Thread.sleep(150);
        String contents = window.stackContents().toString();
        assertFalse(contents.contains("1234"), "the previous sheet came back through undo: " + contents);
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aSheetThatWillNotParseIsReportedRatherThanLoadedEmpty() throws Exception {
        Path file = dir.resolve("broken.calc");
        Files.writeString(file, "this is not a sheet\n");
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.runOnFx(() -> window.submit("42"));
        settle(window, 1);
        // Opening it must not clear the stack that is there. The dialog is suppressed in tests via
        // openSheetQuietly, which is the same path minus the modal.
        FxTestSupport.runOnFx(() -> assertFalse(window.openSheetQuietly(file), "a broken sheet reported success"));
        Thread.sleep(150);
        assertEquals(1, window.stackContents().size(), "a failed open emptied the stack");
        FxTestSupport.runOnFx(window::dispose);
    }
}
