package com.calcula.ui;

import java.util.List;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The completion popup, shown for real.
 *
 * <p>Every other test here stops at a Scene, because that is all the toolkit was thought to allow.
 * It is not: the headless platform will open a {@link Stage}, and a {@link javafx.stage.Popup} needs
 * one — so the thing that could not be tested, and was therefore guessed at twice, can simply be
 * measured.
 *
 * <p>What is measured is whether any row is CLIPPED: a row whose preferred width exceeds the width it
 * was given is text the user cannot read, and no assertion about the popup's own width proves its
 * absence.
 */
@Tag("fx")
class CompletionPopupFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.bootToolkit();
    }

    /** A shown popup, and the rows it is really drawing. */
    private record Shown(InputCompletion completion, List<Region> rows, ListView<?> list) {}

    private static Shown showFor(String typed) throws Exception {
        return FxTestSupport.callOnFx(() -> {
            TextField field = new TextField();
            VBox root = new VBox(new Region(), field);
            Scene scene = new Scene(root, 900, 400);
            Themes.apply(scene, Themes.DEFAULT);
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();

            InputCompletion completion = new InputCompletion(field);
            field.setText(typed);
            field.positionCaret(typed.length());
            completion.update();

            Parent content = (Parent) completion.popupContentForTest();
            content.applyCss();
            content.layout();
            // The measurer carries the same row class and is deliberately zero-sized; counting it as a
            // row would report a clip on every single popup.
            List<Region> rows = content.lookupAll(".completion-row").stream()
                    .filter(n -> !n.getStyleClass().contains("completion-measurer"))
                    .map(Region.class::cast)
                    .toList();
            return new Shown(completion, rows, (ListView<?>) content.lookup(".completion-list"));
        });
    }

    @Test
    void noRowIsClippedByThePopupItIsDrawnIn() throws Exception {
        for (String typed : List.of("s", "i", "e", "a", "co", "de", "m", "p")) {
            Shown shown = showFor(typed);
            assertFalse(shown.rows().isEmpty(), "nothing was drawn for " + typed);
            for (Region row : shown.rows()) {
                double needed = FxTestSupport.callOnFx(() -> row.prefWidth(-1));
                double given = FxTestSupport.callOnFx(row::getWidth);
                assertTrue(
                        needed <= given + 0.5,
                        typed + ": a row needs " + needed + "px and was given " + given + "px — that text is cut off");
            }
        }
    }

    @Test
    void theWidestEntryInTheWholeCatalogueFits() throws Exception {
        // Not just a common prefix: the entry that decides the ceiling.
        Shown shown = showFor("Series");
        for (Region row : shown.rows()) {
            double needed = FxTestSupport.callOnFx(() -> row.prefWidth(-1));
            double given = FxTestSupport.callOnFx(row::getWidth);
            assertTrue(needed <= given + 0.5, "the widest catalogue entry is cut off: " + needed + " > " + given);
        }
    }

    @Test
    void thePopupIsWiderThanTheRowsItHolds() throws Exception {
        Shown shown = showFor("s");
        double list = FxTestSupport.callOnFx(shown.list()::getWidth);
        for (Region row : shown.rows()) {
            assertTrue(FxTestSupport.callOnFx(row::getWidth) <= list, "a row is wider than the list");
        }
    }

    @Test
    void thePopupReallyOpens() throws Exception {
        // The thing two rounds of reasoning could not establish without a Stage.
        Shown shown = showFor("prim");
        assertTrue(FxTestSupport.callOnFx(shown.completion()::isShowing));
    }

    @Test
    void aShortListDoesNotGetAScrollbarsWorthOfEmptySpace() throws Exception {
        Shown shown = showFor("prim");
        // Against the catalogue rather than a number written here, which was 2 until PrimePi joined
        // the table and made this fail for a reason that had nothing to do with layout.
        assertEquals(
                com.calcula.parse.Functions.startingWith("prim").size(),
                shown.rows().size(),
                "one row per match, and no filler");
    }
}
