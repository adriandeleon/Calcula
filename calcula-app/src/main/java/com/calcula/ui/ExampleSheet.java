package com.calcula.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import com.calcula.help.Example;
import com.calcula.help.Examples;

/**
 * Worked examples, filterable, and each one runs when you press it.
 *
 * <p>The sibling of {@link FunctionSheet} and deliberately not the same thing. The function sheet
 * answers "what is this called"; this answers "what is it FOR", which is the question someone actually
 * has when they open a calculator they have never used. Running rather than printing is the whole
 * point — the result lands on the stack, where it can be taken apart, plotted, or copied.
 */
public final class ExampleSheet {

    private static final double CARD_WIDTH = 660;
    private static final double LIST_HEIGHT = 440;

    private final OverlayHost host;
    private final Consumer<Example> onRun;

    private final TextField filter = new TextField();
    private final VBox entries = new VBox();
    private final Label count = new Label();

    private VBox card;

    public ExampleSheet(OverlayHost host, Consumer<Example> onRun) {
        this.host = host;
        this.onRun = onRun;
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

        Label title = new Label("Examples");
        title.getStyleClass().add("settings-title");
        count.getStyleClass().add("settings-note");

        Label note = new Label("Click one to run it. The result goes on the stack, where you can click "
                + "into it, plot it, or copy it out.");
        note.getStyleClass().add("settings-note");
        note.setWrapText(true);

        VBox box = new VBox(title, filter, scroll, count, note);
        box.getStyleClass().add("settings-card");
        box.setPrefWidth(CARD_WIDTH);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        box.setMaxHeight(Region.USE_PREF_SIZE);
        return box;
    }

    private void render(String query) {
        List<Example> shown = Examples.matching(query);
        Map<String, List<Example>> byGroup = new LinkedHashMap<>();
        for (Example example : shown) {
            byGroup.computeIfAbsent(example.group(), g -> new ArrayList<>()).add(example);
        }

        entries.getChildren().clear();
        byGroup.forEach((group, examples) -> {
            Label heading = new Label(group);
            heading.getStyleClass().add("settings-section");
            entries.getChildren().add(heading);
            examples.forEach(example -> entries.getChildren().add(row(example)));
        });
        if (shown.isEmpty()) {
            Label nothing = new Label("Nothing matches " + query);
            nothing.getStyleClass().add("settings-note");
            entries.getChildren().add(nothing);
        }
        count.setText(shown.size() + " of " + Examples.all().size());
    }

    /**
     * One example: what it shows, what gets typed, and what to notice.
     *
     * <p>Stacked rather than in columns. An expression is as long as it is — a column layout either
     * truncates {@code solve(x^2 - 5*x + 6 == 0, x)} or leaves half the row empty for the short ones.
     */
    private Region row(Example example) {
        Label title = new Label(example.title());
        title.getStyleClass().add("example-title");

        Label source = new Label(String.join("   ·   ", example.lines()));
        source.getStyleClass().add("example-source");
        source.setWrapText(true);

        VBox lines = new VBox(2, title, source);
        if (example.note() != null) {
            Label note = new Label(example.note());
            note.getStyleClass().add("example-note");
            note.setWrapText(true);
            lines.getChildren().add(note);
        }
        lines.getStyleClass().addAll("sheet-row", "example-row");
        lines.setOnMouseClicked(e -> {
            host.hide();
            onRun.accept(example);
        });
        return lines;
    }
}
