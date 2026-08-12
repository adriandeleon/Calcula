package com.calcula.ui;

import java.util.List;

import javafx.scene.control.MenuItem;

import com.calcula.machine.TrailEntry;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The right-click menus.
 *
 * <p>The invariant worth pinning is that a menu offers only what genuinely applies to the row that was
 * clicked. Drop and Evaluate act on the TOP of the stack — there is no operation for "delete entry 4" —
 * so offering them on entry 4 would mean quietly acting on entry 1 instead, which is worse than not
 * offering them at all.
 */
@Tag("fx")
class ContextMenuFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    private static List<String> labels(javafx.scene.control.ContextMenu menu) {
        return menu.getItems().stream()
                .map(MenuItem::getText)
                .filter(t -> t != null)
                .toList();
    }

    /** The items inside the named submenu, so a nested entry is reachable to assert on. */
    private static List<String> submenu(javafx.scene.control.ContextMenu menu, String title) {
        return menu.getItems().stream()
                .filter(item -> item instanceof javafx.scene.control.Menu && title.equals(item.getText()))
                .flatMap(item -> ((javafx.scene.control.Menu) item).getItems().stream())
                .map(MenuItem::getText)
                .filter(t -> t != null)
                .toList();
    }

    @Test
    void theTopOfTheStackOffersTheOperationsThatActOnTheTop() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        List<String> items = FxTestSupport.callOnFx(() -> labels(window.stackMenu(Parser.parse("x + 1"), 1)));
        assertTrue(items.contains("Drop"), items.toString());
        assertTrue(items.contains("Evaluate"), items.toString());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aDeeperEntryDoesNotOfferThemRatherThanActingOnTheWrongValue() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        List<String> items = FxTestSupport.callOnFx(() -> labels(window.stackMenu(Parser.parse("x + 1"), 3)));
        assertFalse(items.contains("Drop"), items.toString());
        assertFalse(items.contains("Evaluate"), items.toString());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void whatWorksOnAnyValueIsOfferedOnEveryRow() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        for (int position : new int[] {1, 4}) {
            int at = position;
            List<String> items = FxTestSupport.callOnFx(() -> labels(window.stackMenu(Parser.parse("x + 1"), at)));
            assertTrue(items.contains("Copy"), "row " + at + ": " + items);
            assertTrue(items.contains("Duplicate to top"), "row " + at + ": " + items);
            assertTrue(items.contains("Plot"), "row " + at + ": " + items);
        }
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void everyExportFormatIsReachableFromTheCopySubmenu() throws Exception {
        // Copy and Copy-as-LaTeX used to be separate top-level items that put the SAME thing on the
        // clipboard for a text target, which read as one of them being broken. The formats now sit
        // under one submenu and each puts exactly its own format on.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        List<String> formats =
                FxTestSupport.callOnFx(() -> submenu(window.stackMenu(Parser.parse("x + 1"), 1), "Copy"));
        for (String format : List.of("LaTeX", "MathML", "Typst", "Plain text")) {
            assertTrue(formats.contains("Copy as " + format), format + " missing from " + formats);
        }
        assertTrue(formats.contains("Copy as PNG"), formats.toString());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theTrailOffersItsLineAndTheWholeThing() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        List<String> items =
                FxTestSupport.callOnFx(() -> labels(window.trailMenu(new TrailEntry(TrailEntry.Kind.RESULT, "5/6"))));
        assertTrue(items.contains("Copy line"), items.toString());
        assertTrue(items.contains("Copy whole trail"), items.toString());
        FxTestSupport.runOnFx(window::dispose);
    }
}
