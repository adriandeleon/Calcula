package com.calcula.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import com.calcula.parse.Functions;

/**
 * Everything callable, grouped and filterable — the answer to "what can this do".
 *
 * <p>The catalogue behind it already existed, as the source of the completion popup, but completion
 * only answers a question you already half know: it needs a prefix, so it can tell you how a name is
 * spelled and never that the name exists. This is the same data with the prefix requirement removed.
 *
 * <p>Clicking a row puts the signature on the input line. A reference you have to retype from is a
 * reference you read once.
 */
public final class FunctionSheet {

    private static final double CARD_WIDTH = 620;
    private static final double LIST_HEIGHT = 420;

    private final OverlayHost host;
    private final Consumer<String> onPick;

    private final TextField filter = new TextField();
    private final VBox entries = new VBox();
    private final Label count = new Label();

    private VBox card;

    public FunctionSheet(OverlayHost host, Consumer<String> onPick) {
        this.host = host;
        this.onPick = onPick;
    }

    public void show() {
        if (card == null) {
            card = build();
        }
        filter.clear();
        render("");
        host.show(card, filter::requestFocus, null);
    }

    private VBox build() {
        filter.setPromptText("Filter");
        filter.getStyleClass().add("palette-input");
        filter.textProperty().addListener((o, was, now) -> render(now));

        ScrollPane scroll = new ScrollPane(entries);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(LIST_HEIGHT);
        scroll.getStyleClass().add("sheet-scroll");

        Label title = new Label("Functions");
        title.getStyleClass().add("settings-title");
        count.getStyleClass().add("settings-note");

        Label note = new Label("Click a row to put it on the input line. Names in Capitals are the "
                + "engine's own — they work because an unrecognised name is passed through untouched.");
        note.getStyleClass().add("settings-note");
        note.setWrapText(true);

        VBox box = new VBox(title, filter, scroll, count, note);
        box.getStyleClass().add("settings-card");
        box.setPrefWidth(CARD_WIDTH);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        box.setMaxHeight(Region.USE_PREF_SIZE);
        return box;
    }

    /**
     * Redraw for a filter.
     *
     * <p>Matches the name, the signature AND the summary, so "prime" finds {@code PrimeQ} and
     * "derivative" finds {@code deriv} — which is the point of a reference rather than a completion:
     * you are looking for something by what it does, not by how it is spelled.
     */
    private void render(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        Map<String, List<Functions.Doc>> byGroup = new LinkedHashMap<>();
        int shown = 0;
        for (Functions.Doc doc : Functions.all()) {
            if (!matches(doc, needle)) {
                continue;
            }
            byGroup.computeIfAbsent(doc.group(), g -> new java.util.ArrayList<>())
                    .add(doc);
            shown++;
        }

        entries.getChildren().clear();
        byGroup.forEach((group, docs) -> {
            Label heading = new Label(group);
            heading.getStyleClass().add("settings-section");
            entries.getChildren().add(heading);
            docs.forEach(doc -> entries.getChildren().add(row(doc)));
        });
        if (shown == 0) {
            Label nothing = new Label("Nothing matches " + query);
            nothing.getStyleClass().add("settings-note");
            entries.getChildren().add(nothing);
        }
        count.setText(shown + " of " + Functions.all().size());
    }

    /**
     * Whether an entry answers a query.
     *
     * <p>Matches the summary as well as the name, which is the difference between a reference and a
     * completion: completion needs to know how a name is spelled, a reference is where you go when
     * you do not. "derivative" has to find {@code deriv}, and "prime" has to find {@code PrimeQ}.
     */
    static boolean matches(Functions.Doc doc, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String haystack = (doc.name() + " " + doc.signature() + " " + doc.summary()).toLowerCase(java.util.Locale.ROOT);
        return haystack.contains(query.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private Region row(Functions.Doc doc) {
        Label signature = new Label(doc.signature());
        signature.getStyleClass().add("completion-signature");
        signature.setMinWidth(220);
        Label summary = new Label(doc.summary());
        summary.getStyleClass().add("completion-summary");
        summary.setWrapText(true);
        HBox.setHgrow(summary, Priority.ALWAYS);

        HBox row = new HBox(10, signature, summary);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("sheet-row");
        row.setOnMouseClicked(e -> {
            host.hide();
            onPick.accept(doc.signature());
        });
        return row;
    }
}
