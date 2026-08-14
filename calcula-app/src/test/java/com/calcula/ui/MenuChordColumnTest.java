package com.calcula.ui;

import java.util.List;
import java.util.Map;

import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;

import com.calcula.command.Command;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chord in a column of its own.
 *
 * <p>Written into the label it lands at a different x on every row, so finding a key means reading
 * each line rather than scanning one. Structural rather than visual, because what is being asserted
 * is that the row has the two labels and the spacer between them — the alignment follows from that
 * and from the popup stretching its rows, neither of which a snapshot would explain.
 */
@Tag("fx")
class MenuChordColumnTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    private static CommandMenuBar bar(boolean systemMenu) throws Exception {
        List<Command> commands = List.of(
                Command.of("stack.drop", "Drop", "Remove the top value", () -> {}),
                Command.of("stack.dup", "Duplicate", "Copy the top value", () -> {}));
        return FxTestSupport.callOnFx(
                () -> new CommandMenuBar(commands, Map.of("stack.drop", "C-d"), id -> {}, systemMenu));
    }

    private static MenuItem firstItem(CommandMenuBar bar) {
        return bar.node().getMenus().get(0).getItems().get(0);
    }

    /**
     * A bound command gets a laid-out row; an unbound one does not need one.
     *
     * <p>Skipped when there is no chord because an empty column is a column of nothing, and the row
     * would be paying for a layout to show it.
     */
    @Test
    void aBoundCommandGetsATitleAndAChord() throws Exception {
        CustomMenuItem row = assertInstanceOf(CustomMenuItem.class, firstItem(bar(false)));
        HBox box = assertInstanceOf(HBox.class, row.getContent());
        assertEquals(3, box.getChildren().size(), "title, spacer, chord");
        assertEquals("Drop", ((Label) box.getChildren().get(0)).getText());
        assertEquals("C-d", ((Label) box.getChildren().get(2)).getText());
        assertTrue(
                ((Label) box.getChildren().get(2)).getStyleClass().contains("menu-item-chord"),
                "the chord carries the class that colours it as a key");
    }

    /** An unbound command is a plain item — there is no column to put nothing in. */
    @Test
    void anUnboundCommandStaysPlain() throws Exception {
        assertEquals(
                "Duplicate",
                bar(false).node().getMenus().get(0).getItems().get(1).getText());
    }

    /**
     * On the native bar the chord goes back into the text.
     *
     * <p>The branch that would otherwise be tested on Linux and never on a Mac, or the reverse — a
     * blank menu on one platform is exactly the bug this shape exists to avoid, and leaving it to
     * whichever machine ran the suite would mean nobody sees it until somebody else builds.
     */
    @Test
    void theNativeBarKeepsTheChordInTheText() throws Exception {
        MenuItem item = firstItem(bar(true));
        assertEquals("Drop  (C-d)", item.getText());
        assertTrue(item.getGraphic() == null, "a native item draws itself; a node in it comes out blank");
    }
}
