package com.calcula.ui;

import java.util.List;

import javafx.geometry.Bounds;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Popup;

import com.calcula.parse.Functions;

/**
 * Completes a function name as it is typed, with its signature.
 *
 * <p>This is the fix for a measured hole: typing {@code isprime(97)} gets it back unevaluated, because
 * an unknown head survives the round trip by design — and that same totality is what makes a wrong
 * guess fail in silence. Nothing in the window said the engine spells it {@code PrimeQ}. Now the name
 * appears while it is being typed.
 *
 * <p>A {@link Popup}, unlike the palette and the settings card, and deliberately: it <b>never calls
 * {@code requestFocus}</b>. The Windows focus problem that makes Popups unsafe is a problem about
 * taking focus into a second native window; a popup that only ever displays, and is driven entirely
 * from the text field's own key handler, cannot do that. It also has to escape the window bounds, which
 * an in-scene overlay cannot.
 */
public final class InputCompletion {

    private static final int MAX_ROWS = 8;
    private static final double ROW_HEIGHT = 24;
    private static final double WIDTH = 380;

    private final TextField input;
    private final Popup popup = new Popup();
    private final ListView<Functions.Doc> list = new ListView<>();

    /**
     * What the last {@link #update()} decided to offer.
     *
     * <p>Kept separately from the popup because the two answer different questions. Whether a Popup can
     * appear is JavaFX's business — it needs a real Window, which a headless scene has not got — while
     * what to offer is this class's business and is worth pinning.
     */
    private List<Functions.Doc> candidates = List.of();

    public InputCompletion(TextField input) {
        this.input = input;
        list.setCellFactory(v -> new Row());
        list.setFixedCellSize(ROW_HEIGHT);
        list.setPrefWidth(WIDTH);
        list.getStyleClass().add("completion-list");
        list.setFocusTraversable(false);
        popup.getContent().add(list);
        popup.setAutoHide(true);
        // Inherits the window's stylesheets, so it is themed like everything else.
        popup.setAutoFix(true);
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    /** What is currently on offer, whether or not a window exists to draw it in. */
    public List<Functions.Doc> candidates() {
        return candidates;
    }

    /**
     * What should be offered for a partly-typed name.
     *
     * <p>Empty when the name is already finished: a popup that only restates what has just been typed
     * is in the way. Pure, so the decision can be tested without a window.
     */
    static List<Functions.Doc> offerFor(String word) {
        List<Functions.Doc> hits = Functions.startingWith(word);
        boolean alreadyTyped = hits.size() == 1 && hits.get(0).name().equalsIgnoreCase(word);
        return alreadyTyped ? List.of() : hits;
    }

    /** Offer completions for the word being typed, or hide when there is nothing to offer. */
    public void update() {
        List<Functions.Doc> hits = offerFor(wordBeforeCaret());
        candidates = hits;
        if (hits.isEmpty()) {
            hide();
            return;
        }
        list.getItems().setAll(hits);
        list.getSelectionModel().select(0);
        list.setPrefHeight(Math.min(hits.size(), MAX_ROWS) * ROW_HEIGHT + 2);
        show();
    }

    public void hide() {
        candidates = List.of();
        popup.hide();
    }

    /** Move the highlight. Returns false when nothing is showing, so the caller can do something else. */
    public boolean move(int by) {
        if (!popup.isShowing() || list.getItems().isEmpty()) {
            return false;
        }
        int size = list.getItems().size();
        int next = (list.getSelectionModel().getSelectedIndex() + by + size) % size;
        list.getSelectionModel().select(next);
        list.scrollTo(next);
        return true;
    }

    /**
     * Put the highlighted name into the field, with its brackets and the caret between them.
     *
     * <p>Inserting {@code PrimeQ()} and parking the caret inside is the whole point: the next thing
     * typed is the argument, and nobody has to notice that a bracket was needed.
     */
    public boolean accept() {
        Functions.Doc chosen = list.getSelectionModel().getSelectedItem();
        if (!popup.isShowing() || chosen == null) {
            return false;
        }
        String text = input.getText();
        int caret = input.getCaretPosition();
        int start = caret - wordBeforeCaret().length();
        boolean callable = chosen.signature().contains("(");
        String insert = callable ? chosen.name() + "()" : chosen.name();
        input.setText(text.substring(0, start) + insert + text.substring(caret));
        input.positionCaret(callable ? start + insert.length() - 1 : start + insert.length());
        hide();
        return true;
    }

    /** The identifier immediately before the caret — what is being completed. */
    private String wordBeforeCaret() {
        String text = input.getText();
        int caret = Math.min(input.getCaretPosition(), text.length());
        int start = caret;
        while (start > 0 && isNameChar(text.charAt(start - 1))) {
            start--;
        }
        return text.substring(start, caret);
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private void show() {
        Bounds bounds = input.localToScreen(input.getBoundsInLocal());
        if (bounds == null) {
            return; // not on screen yet
        }
        // Above the field, because the field is at the BOTTOM of the window — a popup below it would
        // fall off the bottom of the screen, and autofix would then cover the thing being typed into.
        double height = list.getPrefHeight();
        popup.show(input, bounds.getMinX(), bounds.getMinY() - height - 4);
    }

    /** Name on the left, signature and summary alongside. */
    private static final class Row extends ListCell<Functions.Doc> {
        private final Label name = new Label();
        private final Label signature = new Label();
        private final Label summary = new Label();
        private final HBox layout;

        Row() {
            name.getStyleClass().add("completion-name");
            signature.getStyleClass().add("completion-signature");
            summary.getStyleClass().add("completion-summary");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            layout = new HBox(8, name, signature, spacer, summary);
            layout.getStyleClass().add("completion-row");
        }

        @Override
        protected void updateItem(Functions.Doc item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            name.setText(item.name());
            // The signature only adds something when it says more than the name already did.
            signature.setText(item.signature().equals(item.name()) ? "" : item.signature());
            summary.setText(item.summary());
            setGraphic(layout);
        }
    }
}
