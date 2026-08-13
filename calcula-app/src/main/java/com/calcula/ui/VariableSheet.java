package com.calcula.ui;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import com.calcula.expr.Expr;
import com.calcula.ui.math.MathLayout;
import com.calcula.ui.math.MathStyle;

/**
 * What is bound, and to what.
 *
 * <p>Storing announced itself once, in the trail, at the moment it happened. After that the only
 * record was the file, and the only way to ask whether {@code n} was bound was to recall it and see
 * whether a number or the bare symbol came back. Three stores and a coffee break was enough to lose
 * track of a session's own state.
 *
 * <p>Values are <b>typeset</b>, at the size and in the display format the stack is using, because a
 * binding is a value and this window's argument is that a value is worth setting properly. They are
 * rendered as a <em>reading</em> rather than as a stack entry: nothing here is addressable, since a
 * click resolving to a path inside a formula that is not on the stack would hand a transform an
 * address into something the user cannot see.
 *
 * <p>Picking a row puts the <b>name</b> on the input line rather than the value. The name is what
 * every next gesture takes — {@code M-s r} to recall it, an expression that mentions it, {@code M-s u}
 * to unbind it — and the value is already on the row being looked at.
 */
public final class VariableSheet {

    private static final double CARD_WIDTH = 560;
    private static final double LIST_HEIGHT = 380;
    private static final double NAME_WIDTH = 120;

    private final OverlayHost host;
    private final Supplier<Map<String, Expr>> bindings;
    private final Supplier<MathStyle> style;
    private final Consumer<String> onPick;
    private final Consumer<String> onRemove;

    private final TextField filter = new TextField();
    private final VBox entries = new VBox();
    private final Label count = new Label();

    private VBox card;

    public VariableSheet(
            OverlayHost host,
            Supplier<Map<String, Expr>> bindings,
            Supplier<MathStyle> style,
            Consumer<String> onPick,
            Consumer<String> onRemove) {
        this.host = host;
        this.bindings = bindings;
        this.style = style;
        this.onPick = onPick;
        this.onRemove = onRemove;
    }

    public void show() {
        if (card == null) {
            card = build();
        }
        filter.clear();
        render("");
        host.show(card, filter::requestFocus, null);
    }

    /**
     * Redraw, if this is on screen.
     *
     * <p>Removing runs through the machine and lands a pulse later, so the row cannot simply delete
     * itself: what the sheet shows has to come from the state that came back, not from the click.
     */
    public void refresh() {
        if (card != null && host.isShowing()) {
            render(filter.getText());
        }
    }

    private VBox build() {
        filter.setPromptText("Filter");
        filter.getStyleClass().add("palette-input");
        filter.textProperty().addListener((o, was, now) -> render(now));

        ScrollPane scroll = new ScrollPane(entries);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(LIST_HEIGHT);
        scroll.getStyleClass().add("sheet-scroll");

        Label title = new Label("Variables");
        title.getStyleClass().add("settings-title");
        count.getStyleClass().add("settings-note");

        Label note = new Label("Click a row to put its name on the input line. A name stays a name in a "
                + "formula until Evaluate is asked for, which is what keeps the algebra working.");
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
        String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        Map<String, Expr> current = bindings.get();

        entries.getChildren().clear();
        int shown = 0;
        for (Map.Entry<String, Expr> binding : current.entrySet()) {
            if (!needle.isEmpty()
                    && !binding.getKey().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                continue;
            }
            entries.getChildren().add(row(binding.getKey(), binding.getValue()));
            shown++;
        }

        if (current.isEmpty()) {
            // The empty state has to say how to leave it. A blank card is indistinguishable from a
            // broken one, and this is the surface somebody reaches when they are not sure the last
            // store worked.
            entries.getChildren()
                    .add(hint("Nothing is bound yet. Put a value on the stack, type a name, "
                            + "and press the store key."));
        } else if (shown == 0) {
            entries.getChildren().add(hint("No name matches " + query));
        }
        count.setText(shown == current.size() ? plural(shown) : shown + " of " + plural(current.size()));
    }

    private static String plural(int n) {
        return n + (n == 1 ? " variable" : " variables");
    }

    private Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("settings-note");
        label.setWrapText(true);
        return label;
    }

    private Region row(String name, Expr value) {
        Label label = new Label(name);
        label.getStyleClass().add("completion-signature");
        label.setMinWidth(NAME_WIDTH);

        Region rendered = MathLayout.renderReading(value, style.get());
        HBox.setHgrow(rendered, Priority.ALWAYS);

        Button remove = new Button("Remove");
        remove.getStyleClass().add("sheet-row-action");
        remove.setOnAction(e -> onRemove.accept(name));
        // Explicitly, rather than trusting a Button to swallow the click on its own: the row beneath
        // it is still a row, and if its handler also ran, removing a binding would hide the sheet and
        // put a name that no longer exists on the input line. The action fires from the release, so
        // stopping the click here does not stop the button working — VariableSheetFxTest pins both
        // halves of that.
        remove.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.event.Event::consume);

        HBox row = new HBox(10, label, rendered, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("sheet-row");
        row.setOnMouseClicked(e -> {
            host.hide();
            onPick.accept(name);
        });
        return row;
    }
}
