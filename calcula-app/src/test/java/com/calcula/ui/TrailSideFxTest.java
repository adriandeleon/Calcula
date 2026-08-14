package com.calcula.ui;

import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.stage.Stage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trail sits to the right of the stack.
 *
 * <p>Which puts the stack's left edge on the same line as the input area below it — those two are one
 * conversation, and the mode line was moved to the frame edge for exactly that reason on the other
 * axis.
 */
@Tag("fx")
class TrailSideFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    @Test
    void theStackIsTheLeftPane() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        SplitPane split = FxTestSupport.callOnFx(window::splitForTest);
        assertEquals(
                window.trailPaneForTest(),
                split.getItems().get(split.getItems().size() - 1),
                "the trail is the right-hand pane");
    }

    /**
     * A saved share is still that share of the window.
     *
     * <p>The trap in the move, and the reason this is measured rather than asserted on the divider:
     * the setting is the trail's share, and while the trail was the left pane that was also the
     * divider's position. On the right the divider is what is left over, so a saved 0.28 pushed
     * straight through opens the trail at 72% — three times the width, on every existing sheet, with
     * nothing in the code looking wrong.
     */
    @Test
    void aSavedShareIsTheWidthItWas() throws Exception {
        CalcWindow window = FxTestSupport.callOnFx(CalcWindow::new);
        double share = window.settings().trailSplit();
        assertTrue(share > 0.1 && share < 0.5, "precondition: the default trail is the narrow pane: " + share);

        FxTestSupport.runOnFx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(window.getRoot(), 1000, 700));
            stage.show();
        });
        // The divider settles on a layout pass and applyTrailLayout sets it a pulse later, so wait for
        // the pane to have a width — NOT for the width to be right, which is what is being asserted.
        // Waiting on the property under test turns a wrong width into a timeout, and a timeout says
        // nothing about what went wrong.
        FxTestSupport.waitFor(
                "the trail to be laid out",
                4000,
                () -> window.trailPaneForTest().getWidth() > 1);

        double width = FxTestSupport.callOnFx(() -> window.trailPaneForTest().getWidth());
        double whole = FxTestSupport.callOnFx(() -> window.splitForTest().getWidth());
        assertEquals(share, width / whole, 0.05, "the trail should still be " + share + " of the window");
    }
}
