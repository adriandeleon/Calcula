package com.calcula.ui;

import java.util.List;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import com.calcula.cas.CasEngine;
import com.calcula.expr.Expr;
import com.calcula.key.KeyDispatcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sheet that says what is bound — and the unbinding that goes with it. */
@Tag("fx")
class VariableSheetFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    private record StubEngine(String id, String version) implements CasEngine {
        @Override
        public Expr eval(Expr input) {
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

    private static CalcWindow window() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        window.setEngine(new StubEngine("stub", "1.0"));
        FxTestSupport.realize(window.getRoot());
        return window;
    }

    private static void settle(String what, java.util.concurrent.Callable<Boolean> done) throws Exception {
        FxTestSupport.waitFor(what, 5000, done);
    }

    /** Put a value on the stack and bind it. */
    private static void store(CalcWindow window, String value, String name) throws Exception {
        FxTestSupport.runOnFx(() -> window.submit(value));
        settle("the value to land", () -> !window.stackContents().isEmpty());
        FxTestSupport.runOnFx(() -> {
            window.type(name);
            window.run("var.store");
        });
        settle("the binding", () -> window.trailContents().stream().anyMatch(l -> l.contains(name + " = ")));
    }

    /**
     * The rows, in the order they are drawn.
     *
     * <p>Depth-first over the children rather than {@code lookupAll}, which returns a <b>Set</b> — so
     * asking it about order gets an answer that is stable enough to look right and is not the one on
     * screen. Two of these tests passed nonsense until that was noticed.
     */
    private static List<Node> rows(CalcWindow window) throws Exception {
        return FxTestSupport.callOnFx(() -> ordered(window.getRoot(), "sheet-row"));
    }

    private static List<Node> ordered(Node root, String styleClass) {
        List<Node> found = new java.util.ArrayList<>();
        collect(root, styleClass, found);
        return found;
    }

    private static void collect(Node node, String styleClass, List<Node> into) {
        if (node.getStyleClass().contains(styleClass)) {
            into.add(node);
        }
        if (node instanceof javafx.scene.Parent p) {
            p.getChildrenUnmodifiable().forEach(child -> collect(child, styleClass, into));
        }
    }

    private static List<String> names(CalcWindow window) throws Exception {
        return FxTestSupport.callOnFx(() -> ordered(window.getRoot(), "sheet-row").stream()
                .flatMap(row -> ordered(row, "completion-signature").stream())
                .filter(Label.class::isInstance)
                .map(n -> ((Label) n).getText())
                .toList());
    }

    @Test
    void everyBindingIsOnTheSheet() throws Exception {
        CalcWindow window = window();
        store(window, "42", "n");
        store(window, "1/3", "third");

        FxTestSupport.runOnFx(() -> window.run("var.list"));
        settle("the sheet", () -> window.overlayShowing());
        assertEquals(List.of("n", "third"), names(window), "in the order they were bound");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void pickingARowPutsTheNameOnTheInputLine() throws Exception {
        // The name and not the value: the name is what the next gesture takes, and the value is
        // already on the row being looked at.
        CalcWindow window = window();
        store(window, "42", "answer");

        FxTestSupport.runOnFx(() -> window.run("var.list"));
        settle("the sheet", () -> window.overlayShowing());
        FxTestSupport.runOnFx(() -> rowFor(window, 0).fireEvent(click()));
        settle("the sheet to close", () -> !window.overlayShowing());
        assertEquals("answer", FxTestSupport.callOnFx(window::typed));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void removingARowUnbindsItAndLeavesTheSheetOpen() throws Exception {
        // Both halves matter. If the button did not swallow the click, the row's own handler would
        // also run: the sheet would close and a name that no longer exists would land on the input
        // line.
        CalcWindow window = window();
        store(window, "42", "n");
        store(window, "7", "k");

        FxTestSupport.runOnFx(() -> window.run("var.list"));
        settle("the sheet", () -> window.overlayShowing());
        FxTestSupport.runOnFx(() -> removeButton(window, 0).fire());
        settle("the row to go", () -> names(window).equals(List.of("k")));

        assertTrue(FxTestSupport.callOnFx(window::overlayShowing), "the sheet should still be up");
        assertEquals("", FxTestSupport.callOnFx(window::typed), "and nothing should have been picked");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void unbindingFromTheInputLineWorksAndSaysSo() throws Exception {
        CalcWindow window = window();
        store(window, "42", "n");

        FxTestSupport.runOnFx(() -> {
            window.type("n");
            window.run("var.clear");
        });
        settle("the note", () -> window.trailContents().stream().anyMatch(l -> l.contains("no longer bound")));

        // And it is really gone: recalling now pushes the bare symbol.
        FxTestSupport.runOnFx(() -> {
            window.type("n");
            window.run("var.recall");
        });
        settle("the recall", () -> !window.stackContents().isEmpty());
        assertEquals(List.of("n"), FxTestSupport.callOnFx(window::stackDisplay));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void unbindingSomethingThatWasNeverBoundSaysSo() throws Exception {
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> {
            window.type("ghost");
            window.run("var.clear");
        });
        settle("the refusal", () -> window.trailContents().stream().anyMatch(l -> l.contains("nothing is bound")));
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void anEmptySheetSaysHowToFillIt() throws Exception {
        CalcWindow window = window();
        FxTestSupport.runOnFx(() -> window.run("var.list"));
        settle("the sheet", () -> window.overlayShowing());

        assertTrue(rows(window).isEmpty());
        assertTrue(
                FxTestSupport.callOnFx(() -> window.getRoot().lookupAll(".settings-note").stream()
                        .anyMatch(n -> n instanceof Label l && l.getText().contains("Nothing is bound"))),
                "a blank card is indistinguishable from a broken one");
        FxTestSupport.runOnFx(window::dispose);
    }

    @Test
    void theChordsReachTheCommands() throws Exception {
        CalcWindow window = window();
        for (String[] pair : List.of(new String[] {"l", "var.list"}, new String[] {"u", "var.clear"})) {
            KeyDispatcher.Result prefix = FxTestSupport.callOnFx(() -> window.press("M-s"));
            assertEquals(KeyDispatcher.Outcome.PENDING, prefix.outcome());

            KeyDispatcher.Result ran = FxTestSupport.callOnFx(() -> window.press(pair[0]));
            assertEquals(KeyDispatcher.Outcome.RAN, ran.outcome(), pair[1]);
            assertEquals(pair[1], ran.commandId());
        }
        FxTestSupport.runOnFx(window::dispose);
    }

    // ---- reaching into the card ------------------------------------------------------------

    private static Node rowFor(CalcWindow window, int index) {
        return ordered(window.getRoot(), "sheet-row").get(index);
    }

    private static Button removeButton(CalcWindow window, int index) {
        return (Button) ordered(rowFor(window, index), "sheet-row-action").get(0);
    }

    private static javafx.scene.input.MouseEvent click() {
        return new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                0,
                0,
                0,
                0,
                javafx.scene.input.MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                null);
    }
}
