package com.calcula.ui;

import java.util.List;

import com.calcula.expr.ExprPath;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Widening, narrowing and clearing a selected part.
 *
 * <p>The property under test is that a move is re-resolved against the CURRENT value of the entry
 * rather than against what was remembered — an entry can be rewritten between one keystroke and the
 * next, and a remembered subterm would then disagree with the address naming it.
 */
@Tag("fx")
class SelectionFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @org.junit.jupiter.api.BeforeEach
    void isolate() throws Exception {
        FxTestSupport.freshConfigDir();
    }

    private static CalcWindow withEntry(String source) throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realize(window.getRoot());
        window.setEngine(new StubEngine());
        FxTestSupport.runOnFx(() -> window.submit(source));
        FxTestSupport.waitFor("the entry", 5000, () -> !window.stackContents().isEmpty());
        return window;
    }

    @Test
    void wideningWalksOutToWhatContainsIt() throws Exception {
        CalcWindow window = withEntry("integrate(x*sin(x), x)");
        FxTestSupport.runOnFx(() -> window.selectPart(1, Parser.parse("x"), List.of(0, 1, 0)));
        assertEquals("x", FxTestSupport.callOnFx(window::selectedPart));

        FxTestSupport.runOnFx(() -> window.run("select.widen"));
        assertEquals("sin(x)", FxTestSupport.callOnFx(window::selectedPart));

        FxTestSupport.runOnFx(() -> window.run("select.widen"));
        assertEquals("x*sin(x)", FxTestSupport.callOnFx(window::selectedPart));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void wideningStopsAtTheWholeFormulaRatherThanFallingOffTheTop() throws Exception {
        CalcWindow window = withEntry("x + 1");
        FxTestSupport.runOnFx(() -> window.selectPart(1, Parser.parse("x + 1"), ExprPath.ROOT));
        FxTestSupport.runOnFx(() -> window.run("select.widen"));
        assertEquals("x + 1", FxTestSupport.callOnFx(window::selectedPart), "still the whole thing");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void narrowingWalksDownTheLeftEdge() throws Exception {
        CalcWindow window = withEntry("integrate(x*sin(x), x)");
        FxTestSupport.runOnFx(() -> window.selectPart(1, Parser.parse("integrate(x*sin(x), x)"), ExprPath.ROOT));

        FxTestSupport.runOnFx(() -> window.run("select.narrow"));
        assertEquals("x*sin(x)", FxTestSupport.callOnFx(window::selectedPart));
        assertEquals(List.of(0), FxTestSupport.callOnFx(window::selectedPath));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void narrowingIntoALeafDoesNothingRatherThanLosingTheSelection() throws Exception {
        CalcWindow window = withEntry("x + 1");
        FxTestSupport.runOnFx(() -> window.selectPart(1, Parser.parse("1"), List.of(1)));
        FxTestSupport.runOnFx(() -> window.run("select.narrow"));
        assertEquals("1", FxTestSupport.callOnFx(window::selectedPart), "a number has no parts");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void clearingSelectsNothing() throws Exception {
        CalcWindow window = withEntry("x + 1");
        FxTestSupport.runOnFx(() -> window.selectPart(1, Parser.parse("x"), List.of(0)));
        FxTestSupport.runOnFx(() -> window.run("select.clear"));
        assertNull(FxTestSupport.callOnFx(window::selectedPart));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void movingWithNothingSelectedSaysSoInsteadOfThrowing() throws Exception {
        CalcWindow window = withEntry("x + 1");
        FxTestSupport.runOnFx(() -> window.run("select.widen"));
        FxTestSupport.waitFor("an explanation", 5000, () -> window.echoNote().contains("nothing is selected"));
        // Said, not written down: a click that found nothing is interface feedback, and the trail
        // is a record of what happened to the mathematics.
        assertTrue(
                window.trailContents().stream().noneMatch(l -> l.contains("nothing is selected")),
                "the explanation belongs in the echo area, not in the record");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theWidenChordIsNotSwallowedByInputHistory() throws Exception {
        // Bare Up recalls history; M-Up widens. Handling the key by code alone would make the chord
        // unreachable, with nothing to say why.
        CalcWindow window = withEntry("x + 1");
        FxTestSupport.runOnFx(() -> window.selectPart(1, Parser.parse("x"), List.of(0)));

        javafx.scene.control.TextField field = (javafx.scene.control.TextField)
                FxTestSupport.callOnFx(() -> window.getRoot().lookup(".echo-input"));
        FxTestSupport.runOnFx(() -> javafx.event.Event.fireEvent(
                field,
                new javafx.scene.input.KeyEvent(
                        javafx.scene.input.KeyEvent.KEY_PRESSED,
                        "",
                        "",
                        javafx.scene.input.KeyCode.UP,
                        false,
                        false,
                        true,
                        false)));

        assertEquals("x + 1", FxTestSupport.callOnFx(window::selectedPart), "M-Up widened");
        assertEquals("", FxTestSupport.callOnFx(window::typed), "and did not recall history");
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
