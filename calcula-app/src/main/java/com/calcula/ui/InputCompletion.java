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

    /** Narrow enough not to loom over a short name; the content decides the rest. */
    private static final double MIN_WIDTH = 320;

    /** Wide enough for the longest entry, short of becoming a second window. */
    private static final double MAX_WIDTH = 760;

    /**
     * Room for the vertical scrollbar.
     *
     * <p>Without it the list is sized to exactly fit its widest row, the scrollbar then takes some of
     * that width away, and the row no longer fits — so avoiding the VERTICAL scrollbar's width is what
     * causes the HORIZONTAL one.
     */
    private static final double SCROLLBAR_ALLOWANCE = 18;

    private final TextField input;
    private final Popup popup = new Popup();
    private final ListView<Functions.Doc> list = new ListView<>();

    /**
     * An off-list copy of a row, used only to measure.
     *
     * <p>It has to live in the popup's scene graph — unmanaged and invisible, so it takes part in no
     * layout — because a node outside a scene gets no CSS, and without CSS its labels have the default
     * font rather than the mono and small faces the real rows use. Measuring with the wrong fonts is
     * how you arrive at a width that is confidently wrong.
     */
    private final RowView measurer = new RowView();

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
        list.setPrefWidth(MIN_WIDTH); // replaced per update by widthFor
        list.getStyleClass().add("completion-list");
        list.setFocusTraversable(false);
        // The measurer rides along in the popup's content so it inherits the stylesheets. Unmanaged,
        // so it contributes nothing to layout, and invisible so it draws nothing.
        measurer.node.setManaged(false);
        measurer.node.setVisible(false);
        javafx.scene.layout.StackPane holder = new javafx.scene.layout.StackPane(list, measurer.node);
        holder.setPickOnBounds(false);
        popup.getContent().add(holder);
        popup.setAutoHide(true);
        // Inherits the window's stylesheets, so it is themed like everything else.
        popup.setAutoFix(true);
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    /** Visible for tests: the width last computed for the popup. */
    public double width() {
        return list.getPrefWidth();
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
        // AFTER show, and it has to be: a Popup's content inherits the owner window's stylesheets only
        // once it is shown, so measuring before that measures unstyled labels — default face instead
        // of the mono signature — and the first popup of a session would be the one sized wrongly.
        // Widening afterwards costs no reposition, since the popup is anchored at the field's left.
        list.setPrefWidth(widthFor(hits));
    }

    public void hide() {
        candidates = List.of();
        popup.hide();
    }

    /**
     * Wide enough for the widest entry on offer, so nothing is clipped and no horizontal scrollbar
     * appears.
     *
     * <p>Measures EVERY candidate rather than the visible ones: the list scrolls, and sizing to the
     * first eight rows means the scrollbar appears the moment you reach a longer one — which is worse
     * than having it all along, because it moves.
     */
    private double widthFor(List<Functions.Doc> hits) {
        measurer.node.applyCss(); // fonts come from CSS, and CSS has not necessarily been applied yet
        double widest = 0;
        for (Functions.Doc doc : hits) {
            measurer.show(doc);
            measurer.node.applyCss();
            widest = Math.max(widest, measurer.node.prefWidth(-1));
        }
        return Math.clamp(widest + SCROLLBAR_ALLOWANCE, MIN_WIDTH, MAX_WIDTH);
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

    /**
     * One row's layout: name, signature, then the summary pushed right.
     *
     * <p>Shared by the cell and the measurer, so the thing being measured is the thing being drawn.
     * Two definitions of a row is two widths, and the wrong one wins silently.
     */
    private static final class RowView {
        private final Label name = new Label();
        private final Label signature = new Label();
        private final Label summary = new Label();
        private final HBox node;

        RowView() {
            name.getStyleClass().add("completion-name");
            signature.getStyleClass().add("completion-signature");
            summary.getStyleClass().add("completion-summary");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            // Nothing ellipsizes: the popup is sized to fit instead. A row that shortened itself would
            // hide the summary, which is the column carrying the answer to "which one do I want".
            for (Label label : List.of(name, signature, summary)) {
                label.setMinWidth(Region.USE_PREF_SIZE);
            }
            node = new HBox(8, name, signature, spacer, summary);
            node.getStyleClass().add("completion-row");
        }

        void show(Functions.Doc doc) {
            name.setText(doc.name());
            // The signature only adds something when it says more than the name already did.
            signature.setText(doc.signature().equals(doc.name()) ? "" : doc.signature());
            summary.setText(doc.summary());
        }
    }

    private static final class Row extends ListCell<Functions.Doc> {
        private final RowView view = new RowView();

        @Override
        protected void updateItem(Functions.Doc item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            view.show(item);
            setGraphic(view.node);
        }
    }
}
