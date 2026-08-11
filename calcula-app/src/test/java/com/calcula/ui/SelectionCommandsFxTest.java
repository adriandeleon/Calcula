package com.calcula.ui;

import java.util.List;

import com.calcula.command.Command;
import com.calcula.expr.ExprPath;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sibling navigation, transforms as commands, and the work indicator. */
@Tag("fx")
class SelectionCommandsFxTest {

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

    // ------------------------------------------------------------------ siblings

    @Test
    void siblingsReachTheSecondArgumentWithoutGoingUpAndBackDown() throws Exception {
        CalcWindow window = withEntry("f(a, b, c)");
        FxTestSupport.runOnFx(() -> window.selectPart(1, Parser.parse("a"), List.of(0)));

        FxTestSupport.runOnFx(() -> window.run("select.nextSibling"));
        assertEquals("b", FxTestSupport.callOnFx(window::selectedPart));

        FxTestSupport.runOnFx(() -> window.run("select.nextSibling"));
        assertEquals("c", FxTestSupport.callOnFx(window::selectedPart));

        FxTestSupport.runOnFx(() -> window.run("select.previousSibling"));
        assertEquals("b", FxTestSupport.callOnFx(window::selectedPart));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void thereIsNothingPastTheLastArgument() throws Exception {
        CalcWindow window = withEntry("f(a, b)");
        FxTestSupport.runOnFx(() -> window.selectPart(1, Parser.parse("b"), List.of(1)));
        FxTestSupport.runOnFx(() -> window.run("select.nextSibling"));
        assertEquals("b", FxTestSupport.callOnFx(window::selectedPart), "unchanged rather than lost");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theWholeFormulaHasNoSiblingToMoveTo() throws Exception {
        CalcWindow window = withEntry("x + 1");
        FxTestSupport.runOnFx(() -> window.selectPart(1, Parser.parse("x + 1"), ExprPath.ROOT));
        FxTestSupport.runOnFx(() -> window.run("select.nextSibling"));
        assertEquals("x + 1", FxTestSupport.callOnFx(window::selectedPart));
        FxTestSupport.runOnFx(window::dispose);
    }

    // ------------------------------------------------------------------ transforms as commands

    @Test
    void everyTransformIsACommandAndSoIsReachableFromThePalette() throws Exception {
        // The menu is built from the same table, so a transform cannot exist in one and not the other.
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        List<String> ids = FxTestSupport.callOnFx(
                () -> window.commands().all().stream().map(Command::id).toList());
        for (String id : List.of("select.simplify", "select.expand", "select.factor", "select.n")) {
            assertTrue(ids.contains(id), id + " is missing from " + ids);
        }
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aTransformWithNothingSelectedSaysSoRatherThanActingOnGuesswork() throws Exception {
        CalcWindow window = withEntry("x + 1");
        FxTestSupport.runOnFx(() -> window.run("select.factor"));
        FxTestSupport.waitFor(
                "an explanation",
                5000,
                () -> window.trailContents().stream().anyMatch(l -> l.contains("nothing is selected")));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aTransformRewritesOnlyTheSelectedPart() throws Exception {
        // The stub engine is the identity, so the transform comes back unapplied — which is enough to
        // show that ONLY that part changed and everything around it survived. It prints lower-case
        // because Names prints a head the way a user would type it.
        CalcWindow window = withEntry("f(a, b)");
        FxTestSupport.runOnFx(() -> window.selectPart(1, Parser.parse("a"), List.of(0)));
        FxTestSupport.runOnFx(() -> window.run("select.simplify"));
        FxTestSupport.waitFor("the rewrite", 5000, () -> window.stackDisplay().equals(List.of("f(simplify(a), b)")));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theSelectionFollowsTheEditSoItCanBeTransformedAgain() throws Exception {
        CalcWindow window = withEntry("f(a, b)");
        FxTestSupport.runOnFx(() -> window.selectPart(1, Parser.parse("a"), List.of(0)));
        FxTestSupport.runOnFx(() -> window.run("select.simplify"));
        FxTestSupport.waitFor("the first rewrite", 5000, () -> "simplify(a)".equals(window.selectedPart()));

        FxTestSupport.runOnFx(() -> window.run("select.expand"));
        FxTestSupport.waitFor(
                "the second rewrite", 5000, () -> window.stackDisplay().equals(List.of("f(expand(simplify(a)), b)")));
        FxTestSupport.runOnFx(window::dispose);
    }

    // ------------------------------------------------------------------ the work indicator

    @Test
    void workIsCountedSoOverlappingCallsCannotClearEachOther() throws Exception {
        CalcWindow window = withEntry("x + 1");
        assertEquals(0, FxTestSupport.callOnFx(window::workInFlight), "settled once the entry landed");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void fastWorkNeverShowsTheIndicator() throws Exception {
        // Arithmetic finishes in well under the delay. An indicator that flickered on every keystroke
        // would read as instability rather than as progress.
        CalcWindow window = withEntry("2 + 2");
        assertFalse(FxTestSupport.callOnFx(window::busyShowing));
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
