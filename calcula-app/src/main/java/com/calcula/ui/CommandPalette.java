package com.calcula.ui;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import com.calcula.command.Command;
import com.calcula.command.CommandRegistry;
import com.calcula.command.CommandSearch;

/**
 * Every command, searchable, with the key that runs it.
 *
 * <p>The complete index — unlike the menu, which is a curated view. Nothing is hidden from it, because
 * the point of a palette in a keyboard-driven application is that it answers "what can this thing do"
 * without anyone having to have read the manual.
 *
 * <p>Each row shows its <b>binding</b>. That is what makes the palette a teaching surface rather than a
 * competing way to work: someone who reaches for it repeatedly is being told, every time, the chord
 * that would have been quicker.
 */
public final class CommandPalette {

    /** Enough rows to browse, few enough that the card does not become the window. */
    private static final int VISIBLE_ROWS = 12;

    /**
     * Row height, fixed rather than measured.
     *
     * <p>Two things follow from fixing it, and both matter: the card's height is exact — a guess left
     * the last row sliced in half and put a scrollbar next to twelve rows that were supposed to fit —
     * and a ListView only virtualises when it knows its cell height, so this is also what stops it
     * building a cell per command.
     */
    private static final double ROW_HEIGHT = 26;

    private static final double CARD_WIDTH = 560;

    private final CommandRegistry registry;
    private final Supplier<Map<String, String>> bindings;
    private final OverlayHost host;
    private final Consumer<String> run;

    private final TextField query = new TextField();
    private final ObservableList<Command> results = FXCollections.observableArrayList();
    private final ListView<Command> list = new ListView<>(results);
    private final VBox card = new VBox();

    private Map<String, String> currentBindings = Map.of();

    public CommandPalette(
            CommandRegistry registry, Supplier<Map<String, String>> bindings, OverlayHost host, Consumer<String> run) {
        this.registry = registry;
        this.bindings = bindings;
        this.host = host;
        this.run = run;
        build();
    }

    private void build() {
        query.setPromptText("Command");
        query.getStyleClass().add("palette-input");
        query.textProperty().addListener((o, was, now) -> refilter());
        query.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);

        list.getStyleClass().add("palette-list");
        list.setCellFactory(v -> new Row());
        list.setFixedCellSize(ROW_HEIGHT);
        list.setFocusTraversable(false); // the field keeps focus; the list is only ever driven from it
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                accept();
            }
        });

        Label hint = new Label("↑↓ or C-n/C-p to move · Enter to run · Esc to close");
        hint.getStyleClass().add("palette-hint");

        card.getStyleClass().add("command-palette");
        card.getChildren().addAll(query, list, hint);
        card.setPrefWidth(CARD_WIDTH);
        card.setMaxWidth(Region.USE_PREF_SIZE);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        VBox.setVgrow(list, Priority.ALWAYS);
    }

    /** Open it, empty, with everything listed. */
    public void show() {
        currentBindings = bindings.get();
        query.clear();
        refilter();
        list.setPrefHeight(VISIBLE_ROWS * ROW_HEIGHT + 2);
        host.show(card, query::requestFocus, null);
    }

    private void refilter() {
        results.setAll(CommandSearch.search(query.getText(), registry.all()));
        if (!results.isEmpty()) {
            list.getSelectionModel().select(0);
            list.scrollTo(0);
        }
    }

    private void onKey(KeyEvent event) {
        switch (event.getCode()) {
            case DOWN -> move(1, event);
            case UP -> move(-1, event);
            case N -> {
                if (event.isControlDown()) {
                    move(1, event);
                }
            }
            case P -> {
                if (event.isControlDown()) {
                    move(-1, event);
                }
            }
            case ENTER -> {
                accept();
                event.consume();
            }
            default -> {
                // Anything else is typing, and belongs to the field. Escape and C-g are handled by the
                // host, so that every overlay dismisses the same way rather than each inventing its own.
            }
        }
    }

    private void move(int by, KeyEvent event) {
        if (results.isEmpty()) {
            return;
        }
        int size = results.size();
        // Wrapping, because a list that stops dead at the end makes you look at where the cursor is;
        // one that wraps lets you hold the key down.
        int next = (list.getSelectionModel().getSelectedIndex() + by + size) % size;
        list.getSelectionModel().select(next);
        list.scrollTo(next);
        event.consume();
    }

    private void accept() {
        Command chosen = list.getSelectionModel().getSelectedItem();
        if (chosen == null) {
            return;
        }
        // Close FIRST: the command may report to the echo area or open something of its own, and
        // running it under a still-visible overlay would hide its own feedback behind the backdrop.
        host.hide();
        run.accept(chosen.id());
    }

    /** Title on the left, description in the middle, binding pinned right. */
    private final class Row extends ListCell<Command> {
        private final Label title = new Label();
        private final Label description = new Label();
        private final Label binding = new Label();
        private final HBox layout = new HBox(title, description, spacer(), binding);

        Row() {
            title.getStyleClass().add("palette-title");
            description.getStyleClass().add("palette-desc");
            binding.getStyleClass().add("palette-binding");
            layout.setAlignment(Pos.CENTER_LEFT);
            layout.getStyleClass().add("palette-row");
        }

        @Override
        protected void updateItem(Command item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            title.setText(item.title());
            description.setText(item.description());
            binding.setText(currentBindings.getOrDefault(item.id(), ""));
            setGraphic(layout);
        }
    }

    private static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }
}
