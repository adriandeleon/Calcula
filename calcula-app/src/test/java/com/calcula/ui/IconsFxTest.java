package com.calcula.ui;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That every glyph draws something, and that the buttons carrying them teach their chord.
 *
 * <p>The first is not a formality. JavaFX's {@link SVGPath} parser rejects path data a browser would
 * accept — a compacted elliptical arc with its flags run together is the classic case — and it
 * renders NOTHING rather than throwing. An icon that silently fails to draw looks exactly like an
 * icon nobody added.
 */
@Tag("fx")
class IconsFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @Test
    void everyGlyphParsesToSomethingWithSize() throws Exception {
        for (String name : Icons.names()) {
            Node icon = FxTestSupport.callOnFx(() -> Icons.of(name));
            var bounds = FxTestSupport.callOnFx(icon::getBoundsInLocal);
            assertTrue(bounds.getWidth() > 1, name + " drew nothing — check its arc flags are spaced");
            assertTrue(bounds.getHeight() > 1, name + " drew nothing — check its arc flags are spaced");
        }
    }

    @Test
    void everyGlyphFitsTheGrid() throws Exception {
        // A glyph that overruns the grid is the one that makes a row of icons look misaligned.
        for (String name : Icons.names()) {
            Node icon = FxTestSupport.callOnFx(() -> Icons.of(name));
            var bounds = FxTestSupport.callOnFx(icon::getBoundsInLocal);
            assertTrue(bounds.getMaxX() <= Icons.SIZE + 1, name + " is wider than the grid: " + bounds);
            assertTrue(bounds.getMaxY() <= Icons.SIZE + 1, name + " is taller than the grid: " + bounds);
        }
    }

    @Test
    void aGlyphIsStrokedRatherThanFilled() throws Exception {
        // A fill rule applied to these paints every outline solid, which is the one mistake that makes
        // a whole set look wrong at once.
        Node icon = FxTestSupport.callOnFx(() -> Icons.of("settings"));
        assertTrue(icon.getStyleClass().contains("icon-line"));
    }

    @Test
    void eachCallReturnsAFreshNode() throws Exception {
        // A Node belongs to one parent, so a cached glyph would move itself out of whichever button
        // was built first — an icon that vanishes the moment a second one appears.
        Node first = FxTestSupport.callOnFx(() -> Icons.of("settings"));
        Node second = FxTestSupport.callOnFx(() -> Icons.of("settings"));
        assertNotSame(first, second);
    }

    @Test
    void anUnknownNameIsATypoAndSaysSo() {
        assertThrows(IllegalArgumentException.class, () -> Icons.of("nosuchglyph"));
    }

    @Test
    void everyChromeButtonCarriesAGlyphAndATooltipNamingItsChord() throws Exception {
        // The reason these buttons are allowed to exist at all: they say a thing exists AND tell you
        // the key that reaches it faster.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Region root = window.getRoot();
        FxTestSupport.realize(root);

        var buttons = FxTestSupport.callOnFx(() -> root.lookupAll(".chrome-button"));
        assertFalse(buttons.isEmpty(), "no chrome buttons were built");
        for (Node node : buttons) {
            Button button = (Button) node;
            assertNotNull(button.getGraphic(), "a chrome button with no glyph");
            Tooltip tip = button.getTooltip();
            assertNotNull(tip, "a chrome button with no tooltip");
            assertFalse(tip.getText().isBlank());
        }
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void everyToolbarButtonIsLabelledAsWellAsDrawn() throws Exception {
        // Icon-only is a guess, and these are exactly the surfaces someone reaches for when they do
        // not yet know what the application can do — the worst moment to make them hover four
        // unfamiliar glyphs to find out.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Region root = window.getRoot();
        FxTestSupport.realize(root);
        for (Node node : FxTestSupport.callOnFx(() -> root.lookupAll(".chrome-button"))) {
            Button button = (Button) node;
            assertFalse(button.getText() == null || button.getText().isBlank(), "an unlabelled toolbar button");
        }
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theToolbarSitsAtTheTopOfTheWindow() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Region root = window.getRoot();
        FxTestSupport.realize(root);
        Node toolbar = FxTestSupport.callOnFx(() -> root.lookup(".toolbar"));
        assertNotNull(toolbar, "no toolbar was built");
        // Above the stack, which is the whole point of moving it out of the mode line.
        double toolbarY = FxTestSupport.callOnFx(() -> toolbar.localToScene(toolbar.getBoundsInLocal()).getMinY());
        double stackY = FxTestSupport.callOnFx(() -> {
            Node stack = root.lookup(".stack-view");
            return stack.localToScene(stack.getBoundsInLocal()).getMinY();
        });
        assertTrue(toolbarY < stackY, "the toolbar is not above the stack");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theChromeButtonsRunRealCommands() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        Region root = window.getRoot();
        FxTestSupport.realize(root);

        Button settings = (Button) FxTestSupport.callOnFx(() -> root.lookupAll(".chrome-button").stream()
                .map(Button.class::cast)
                .filter(b -> b.getTooltip().getText().startsWith("Settings"))
                .findFirst()
                .orElseThrow());
        FxTestSupport.runOnFx(settings::fire);
        assertTrue(FxTestSupport.callOnFx(window::overlayShowing), "the settings button opened nothing");
        FxTestSupport.runOnFx(window::closeOverlay);
        FxTestSupport.runOnFx(window::dispose);
    }
}
