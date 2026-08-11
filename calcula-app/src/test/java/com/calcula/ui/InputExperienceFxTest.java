package com.calcula.ui;

import java.util.List;

import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import com.calcula.parse.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Input history, completion, and the subterm-aware right-click menu. */
@Tag("fx")
class InputExperienceFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @org.junit.jupiter.api.BeforeEach
    void isolate() throws Exception {
        FxTestSupport.freshConfigDir();
    }

    private static void press(CalcWindow window, KeyCode code) throws Exception {
        javafx.scene.control.TextField input = (javafx.scene.control.TextField)
                FxTestSupport.callOnFx(() -> window.getRoot().lookup(".echo-input"));
        FxTestSupport.runOnFx(() -> javafx.event.Event.fireEvent(
                input, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false)));
    }

    // ------------------------------------------------------------------ history

    @Test
    void upWalksBackThroughWhatWasTyped() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        window.setEngine(new StubEngine());

        for (String line : List.of("1 + 1", "2 + 2")) {
            FxTestSupport.runOnFx(() -> window.submit(line));
            FxTestSupport.waitFor("entry", 5000, () -> !window.stackContents().isEmpty());
        }

        press(window, KeyCode.UP);
        assertEquals("2 + 2", FxTestSupport.callOnFx(window::typed), "the most recent line first");
        press(window, KeyCode.UP);
        assertEquals("1 + 1", FxTestSupport.callOnFx(window::typed));
        press(window, KeyCode.DOWN);
        assertEquals("2 + 2", FxTestSupport.callOnFx(window::typed));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void walkingPastTheNewestLeavesAnEmptyLineToTypeInto() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        window.setEngine(new StubEngine());
        FxTestSupport.runOnFx(() -> window.submit("1 + 1"));
        FxTestSupport.waitFor("entry", 5000, () -> !window.stackContents().isEmpty());

        press(window, KeyCode.UP);
        press(window, KeyCode.DOWN);
        assertEquals("", FxTestSupport.callOnFx(window::typed), "back to a fresh line, not stuck on the oldest");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theSameLineTwiceIsOneHistoryEntry() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        window.setEngine(new StubEngine());
        for (int i = 0; i < 2; i++) {
            FxTestSupport.runOnFx(() -> window.submit("1 + 1"));
            FxTestSupport.waitFor("entry", 5000, () -> !window.stackContents().isEmpty());
        }
        press(window, KeyCode.UP);
        press(window, KeyCode.UP);
        assertEquals("1 + 1", FxTestSupport.callOnFx(window::typed));
        FxTestSupport.runOnFx(window::dispose);
    }

    // ------------------------------------------------------------------ completion

    @Test
    void typingAPrefixOffersTheNamesThatMatch() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        FxTestSupport.runOnFx(() -> window.type("prim"));
        // The offer, not the Popup: a Popup needs a real Window, which a headless scene has not got.
        List<String> offered = FxTestSupport.callOnFx(window::completionCandidates);
        assertTrue(offered.contains("PrimeQ"), offered.toString());
        assertTrue(offered.contains("Prime"), offered.toString());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aNameAlreadyFinishedIsNotWorthAPopup() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        FxTestSupport.runOnFx(() -> window.type("Fibonacci"));
        assertTrue(FxTestSupport.callOnFx(window::completionCandidates).isEmpty(), "it has already been typed");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void enterSubmitsRatherThanAcceptingASuggestion() throws Exception {
        // Enter is THE action in a calculator. Turning it into "accept the suggestion you were
        // ignoring" is the surprise that makes people stop trusting the input line.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        window.setEngine(new StubEngine());
        FxTestSupport.runOnFx(() -> window.type("2 + 2"));
        FxTestSupport.runOnFx(() -> window.run("input.submit"));
        FxTestSupport.waitFor("the sum", 5000, () -> window.stackDisplay().equals(List.of("4")));
        FxTestSupport.runOnFx(window::dispose);
    }

    // ------------------------------------------------------------------ subterms

    @Test
    void rightClickingAPartOffersThatPart() throws Exception {
        // The payoff for setting formulas as mathematics rather than printing them as text.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());

        List<String> items = FxTestSupport.callOnFx(() ->
                window.stackMenu(Parser.parse("integrate(x*sin(x), x)"), 1, Parser.parse("sin(x)")).getItems().stream()
                        .map(MenuItem::getText)
                        .filter(t -> t != null)
                        .toList());

        assertTrue(items.contains("Extract  sin(x)"), items.toString());
        assertTrue(items.contains("Copy  sin(x)"), items.toString());
        assertTrue(items.contains("Plot  sin(x)"), items.toString());
        assertTrue(items.contains("Copy"), "and the whole entry is still there: " + items);
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void clickingTheWholeFormulaDoesNotOfferItTwice() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        List<String> items = FxTestSupport.callOnFx(
                () -> window.stackMenu(Parser.parse("x + 1"), 1, Parser.parse("x + 1")).getItems().stream()
                        .map(MenuItem::getText)
                        .filter(t -> t != null)
                        .toList());
        assertFalse(items.stream().anyMatch(t -> t.startsWith("Extract")), items.toString());
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aLongSubtermIsTruncatedInTheLabelRatherThanFloodingTheMenu() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        List<String> items = FxTestSupport.callOnFx(() -> window
                .stackMenu(
                        Parser.parse("f(aaaaaaaaaa + bbbbbbbbbb + cccccccccc + dddddddddd)"),
                        1,
                        Parser.parse("aaaaaaaaaa + bbbbbbbbbb + cccccccccc + dddddddddd"))
                .getItems()
                .stream()
                .map(MenuItem::getText)
                .filter(t -> t != null)
                .toList());
        assertTrue(items.stream().anyMatch(t -> t.startsWith("Extract") && t.contains("…")), items.toString());
        FxTestSupport.runOnFx(window::dispose);
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
