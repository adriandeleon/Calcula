package com.calcula.ui;

import java.util.List;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The input line's own menu.
 *
 * <p>JavaFX builds one for a text field and it carries no icons — the one menu in the application
 * that looked like it came from a different program. A field shows its own only when none has been
 * set, so the test worth having is that ours IS set, and that every item in it has a glyph.
 */
@Tag("fx")
class InputMenuFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @BeforeEach
    void isolate() throws Exception {
        FxTestSupport.freshConfigDir();
    }

    private static ContextMenu menu(CalcWindow window) throws Exception {
        return FxTestSupport.callOnFx(() -> window.inputField().getContextMenu());
    }

    @Test
    void theInputLineHasOurMenuRatherThanTheOneJavaFxBuilds() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        assertNotNull(menu(window), "the field kept its stock menu");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void everyItemCarriesAGlyph() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        for (MenuItem item : menu(window).getItems()) {
            if (item instanceof SeparatorMenuItem) {
                continue;
            }
            assertNotNull(item.getGraphic(), item.getText() + " has no icon");
        }
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void itOffersWhatATextFieldShouldOffer() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        List<String> labels = menu(window).getItems().stream()
                .map(MenuItem::getText)
                .filter(t -> t != null)
                .toList();
        assertTrue(
                labels.containsAll(List.of("Undo", "Redo", "Cut", "Copy", "Paste", "Select All")), labels.toString());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void whatCannotWorkIsGreyedOutRatherThanOffered() throws Exception {
        // A Cut with nothing selected is a small lie the menu is in a position to avoid.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        ContextMenu menu = menu(window);

        FxTestSupport.runOnFx(() -> {
            window.inputField().setText("");
            menu.getOnShowing().handle(null);
        });
        assertTrue(item(menu, "Cut").isDisable(), "Cut with no selection");
        assertTrue(item(menu, "Select All").isDisable(), "Select All on an empty line");

        FxTestSupport.runOnFx(() -> {
            window.inputField().setText("x + 1");
            window.inputField().selectAll();
            menu.getOnShowing().handle(null);
        });
        assertFalse(item(menu, "Cut").isDisable(), "Cut with a selection");
        assertFalse(item(menu, "Select All").isDisable());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void clearEmptiesTheLine() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        ContextMenu menu = menu(window);
        FxTestSupport.runOnFx(() -> {
            window.inputField().setText("x + 1");
            item(menu, "Clear").fire();
        });
        assertEquals("", FxTestSupport.callOnFx(() -> window.inputField().getText()));
        FxTestSupport.runOnFx(window::dispose);
    }

    private static MenuItem item(ContextMenu menu, String label) {
        return menu.getItems().stream()
                .filter(i -> label.equals(i.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no item called " + label));
    }
}
