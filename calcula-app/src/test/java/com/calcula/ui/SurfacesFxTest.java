package com.calcula.ui;

import java.util.List;
import java.util.Set;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;

import com.calcula.command.Command;
import com.calcula.command.CommandGroups;
import com.calcula.command.CommandRegistry;
import com.calcula.machine.Modes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The menu, the palette and the overlay host.
 *
 * <p>The property worth testing across all of them is that they are <b>views of the registry</b> rather
 * than parallel implementations — because the failure mode of getting that wrong is silent: a menu that
 * still offers a command that was renamed, or that never offers one that was added.
 */
@Tag("fx")
class SurfacesFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    /** Several of these SAVE settings, and a shared JVM would carry that into the next test. */
    @org.junit.jupiter.api.BeforeEach
    void isolate() throws Exception {
        FxTestSupport.freshConfigDir();
    }

    // ------------------------------------------------------------------ the menu is generated

    @Test
    void everyMenuableCommandReachesTheMenu() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());

        Set<String> inMenus = FxTestSupport.callOnFx(() -> menuLabels(window));
        for (Command command : FxTestSupport.callOnFx(() -> window.commands().all())) {
            if (CommandGroups.menuFor(command.id()) != null) {
                assertTrue(
                        inMenus.stream().anyMatch(l -> l.startsWith(command.title())),
                        command.title() + " is registered and grouped but absent from the menu: " + inMenus);
            }
        }
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theEnterKeyIsNotOfferedAsAMenuItem() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        assertFalse(
                FxTestSupport.callOnFx(() -> menuLabels(window)).stream().anyMatch(l -> l.startsWith("Enter")),
                "a menu is a curated view; the palette is the complete index");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void eachMenuItemCarriesTheChordThatRunsIt() throws Exception {
        // What makes the menu a teaching surface rather than a competing way to work.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        Set<String> labels = FxTestSupport.callOnFx(() -> menuLabels(window));
        // C-z, not C-x u: undo has both, and the one-chord binding is the one worth teaching.
        assertTrue(labels.stream().anyMatch(l -> l.equals("Undo  (C-z)")), labels.toString());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void noMenuItemInstallsAnAcceleratorOfItsOwn() throws Exception {
        // KeyDispatcher is the only thing that dispatches a key. A second, parallel path could
        // disagree with the keymap — and most bindings here are multi-key sequences that a
        // KeyCombination cannot express at all.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        FxTestSupport.runOnFx(() -> {
            for (MenuItem item : allItems(window)) {
                assertEquals(null, item.getAccelerator(), item.getText() + " installed an accelerator");
            }
        });
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aMenuItemRunsTheSameCommandTheKeyboardWould() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        window.setEngine(new StubEngine());

        FxTestSupport.runOnFx(() -> item(window, "Degrees").fire());
        FxTestSupport.waitFor("the mode line", 5000, () -> window.modeLine().startsWith("deg"));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theModeMenuShowsWhichModeIsCurrent() throws Exception {
        // A mode menu that does not show the current mode invites you to set what is already set.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());

        assertTrue(FxTestSupport.callOnFx(() -> ((RadioMenuItem) item(window, "Radians")).isSelected()));

        FxTestSupport.runOnFx(() -> window.run("mode.degrees"));
        FxTestSupport.waitFor("degrees", 5000, () -> window.modeLine().startsWith("deg"));
        FxTestSupport.runOnFx(() -> {
            assertTrue(((RadioMenuItem) item(window, "Degrees")).isSelected());
            assertFalse(((RadioMenuItem) item(window, "Radians")).isSelected(), "radio, not three checkboxes");
        });
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theCheckableModesFollowTheMachineRatherThanTheirOwnClicks() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());

        assertTrue(FxTestSupport.callOnFx(() -> ((CheckMenuItem) item(window, "Toggle fractions")).isSelected()));
        FxTestSupport.runOnFx(() -> window.run("mode.fractions"));
        FxTestSupport.waitFor("fractions off", 5000, () -> !window.modeLine().contains("frac"));
        assertFalse(FxTestSupport.callOnFx(() -> ((CheckMenuItem) item(window, "Toggle fractions")).isSelected()));
        FxTestSupport.runOnFx(window::dispose);
    }

    // ------------------------------------------------------------------ the palette

    @Test
    void thePaletteOpensAndClosesThroughTheOverlayHost() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());

        assertFalse(FxTestSupport.callOnFx(() -> window.overlayShowing()));
        FxTestSupport.runOnFx(() -> window.run("app.palette"));
        assertTrue(FxTestSupport.callOnFx(() -> window.overlayShowing()), "M-x should open it");

        FxTestSupport.runOnFx(() -> window.closeOverlay());
        assertFalse(FxTestSupport.callOnFx(() -> window.overlayShowing()));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void thePaletteIsTheCompleteIndexIncludingWhatTheMenuHides() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        CommandRegistry registry = FxTestSupport.callOnFx(window::commands);
        assertTrue(
                registry.all().stream().anyMatch(c -> c.id().equals("input.submit")),
                "the palette lists the registry, and the registry has Enter in it");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void settingsOpenAsAnOverlayToo() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        FxTestSupport.runOnFx(() -> window.run("app.settings"));
        assertTrue(FxTestSupport.callOnFx(() -> window.overlayShowing()));
        assertNotNull(FxTestSupport.callOnFx(() -> window.getRoot().lookup(".settings-card")));
        FxTestSupport.runOnFx(window::closeOverlay);
        FxTestSupport.runOnFx(window::dispose);
    }

    // ------------------------------------------------------------------ settings reach the window

    @Test
    void changingTheEntryModelInSettingsTakesEffectImmediately() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        assertEquals("algebraic", FxTestSupport.callOnFx(window::readerId));

        FxTestSupport.runOnFx(() -> window.applySettings(window.settings().withInputModel("rpn")));
        assertEquals("rpn", FxTestSupport.callOnFx(window::readerId), "it governs the next line typed");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aSavedThemeIsHandedBackToWhoeverOwnsTheScene() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        Themes[] applied = new Themes[1];
        FxTestSupport.runOnFx(() -> {
            window.setOnThemeChanged(t -> applied[0] = t);
            window.applySettings(window.settings().withTheme("plate"));
        });
        assertEquals(Themes.PLATE, applied[0]);
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void changingADefaultModeDoesNotReachIntoTheRunningSession() throws Exception {
        // Defaults are what a NEW session starts from. Reaching into this one would mean a preference
        // change landed in the undo history, and that undoing it rewrote the preference.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        assertTrue(window.modeLine().startsWith("rad"));

        FxTestSupport.runOnFx(
                () -> window.applySettings(window.settings().withModes(Modes.DEFAULTS.withAngle(Modes.Angle.DEGREES))));
        assertTrue(window.modeLine().startsWith("rad"), "the session keeps its own modes");
        FxTestSupport.runOnFx(window::dispose);
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static MenuBar bar(CalcWindow window) {
        return (MenuBar) window.getRoot().lookup(".calc-menu-bar");
    }

    private static List<MenuItem> allItems(CalcWindow window) {
        return bar(window).getMenus().stream()
                .flatMap(m -> m.getItems().stream())
                .toList();
    }

    private static Set<String> menuLabels(CalcWindow window) {
        return allItems(window).stream().map(MenuItem::getText).collect(java.util.stream.Collectors.toSet());
    }

    private static MenuItem item(CalcWindow window, String titlePrefix) {
        return allItems(window).stream()
                .filter(i -> i.getText() != null && i.getText().startsWith(titlePrefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no menu item starting with " + titlePrefix));
    }

    /** Identity, so the UI can be driven without paying Symja's start-up. */
    private record StubEngine() implements com.calcula.cas.CasEngine {
        @Override
        public String id() {
            return "stub";
        }

        @Override
        public String version() {
            return "1.0";
        }

        @Override
        public com.calcula.expr.Expr eval(com.calcula.expr.Expr input) {
            return input;
        }

        @Override
        public String texForm(com.calcula.expr.Expr input) {
            return "";
        }

        @Override
        public String mathmlForm(com.calcula.expr.Expr input) {
            return "";
        }
    }
}
