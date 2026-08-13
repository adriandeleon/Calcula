package com.calcula.ui;

import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import com.calcula.expr.Expr;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a row says it came from.
 *
 * <p>{@link com.calcula.machine.ProvenanceTest} covers the machine keeping the history; this covers
 * the row saying it — and, as much to the point, staying quiet when there is nothing to say.
 */
@Tag("fx")
class ProvenanceRowFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @BeforeEach
    void isolate() throws Exception {
        FxTestSupport.freshConfigDir();
    }

    /** Identity, so the value is whatever was typed and the origin is the only thing that differs. */
    private record StubEngine(String id, String version) implements CasEngine {
        @Override
        public Expr eval(Expr input) throws CasException {
            return input;
        }

        @Override
        public String texForm(Expr input) {
            return "";
        }

        @Override
        public String mathmlForm(Expr input) {
            return "";
        }
    }

    private static CalcWindow open() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        FxTestSupport.realizeThemed(window.getRoot(), Themes.SLAB);
        window.setEngine(new StubEngine("stub", "1.0"));
        return window;
    }

    /** The tooltip on the topmost realised row, or null. */
    private static String toldOf(CalcWindow window) throws Exception {
        return FxTestSupport.callOnFx(() -> {
            window.getRoot().applyCss();
            window.getRoot().layout();
            for (Node n : window.getRoot().lookupAll(".stack-view .list-cell")) {
                if (n instanceof ListCell<?> cell && cell.getGraphic() instanceof Region row) {
                    Tooltip t = (Tooltip) row.getProperties().get("javafx.scene.control.Tooltip");
                    return t == null ? null : t.getText();
                }
            }
            return null;
        });
    }

    private static String spokenOf(CalcWindow window) throws Exception {
        return FxTestSupport.callOnFx(() -> {
            window.getRoot().applyCss();
            window.getRoot().layout();
            for (Node n : window.getRoot().lookupAll(".stack-view .list-cell")) {
                if (n instanceof ListCell<?> cell && cell.getGraphic() instanceof Region row) {
                    return row.getAccessibleText();
                }
            }
            return null;
        });
    }

    @Test
    void aWorkedOutValueSaysWhatItCameFrom() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit("1/3 + 1/6"));
        FxTestSupport.waitFor("the value", 5000, () -> !window.stackContents().isEmpty());

        String told = toldOf(window);
        assertNotNull(told, "a value that came from somewhere should be able to say so");
        assertTrue(told.contains("1/3 + 1/6"), told);
        FxTestSupport.runOnFx(window::dispose);
    }

    /**
     * The refusal that keeps it quiet. Typing 42 produces 42, and "from: 42" beside 42 would be a
     * tooltip on every row in the window saying nothing.
     */
    @Test
    void aValueThatIsItsOwnOriginSaysNothing() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit("42"));
        FxTestSupport.waitFor("the value", 5000, () -> !window.stackContents().isEmpty());

        assertNull(toldOf(window), "no tooltip at all on a row with nothing to add");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void aScreenReaderIsToldWhereItCameFromToo() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit("1/3 + 1/6"));
        FxTestSupport.waitFor("the value", 5000, () -> !window.stackContents().isEmpty());

        String said = spokenOf(window);
        assertNotNull(said);
        assertTrue(said.startsWith("1: "), said);
        assertTrue(said.contains("1/3 + 1/6"), said);
        FxTestSupport.runOnFx(window::dispose);
    }

    /**
     * A sheet loaded from a file has values and no history, and the window says nothing rather than
     * inventing an answer. Provenance is session-only, which is what keeps the .calc format — and its
     * round-trip property — untouched.
     */
    @Test
    void aValueRestoredFromASheetHasNoHistoryToShow() throws Exception {
        CalcWindow window = open();
        FxTestSupport.runOnFx(() -> window.submit("1/3 + 1/6"));
        FxTestSupport.waitFor("the value", 5000, () -> !window.stackContents().isEmpty());
        assertNotNull(toldOf(window), "precondition: it knows now");

        // Round-tripping through the sheet is what drops it.
        com.calcula.doc.Sheet sheet = com.calcula.doc.Sheet.of(
                com.calcula.machine.CalcState.ofValues(
                        window.stackContents(), java.util.Map.of(), com.calcula.machine.Modes.DEFAULTS),
                java.util.List.of());
        assertEquals(1, sheet.state().depth());
        assertNull(sheet.state().entryAt(1).origin(), "a file remembers the mathematics, not the history");
        FxTestSupport.runOnFx(window::dispose);
    }
}
