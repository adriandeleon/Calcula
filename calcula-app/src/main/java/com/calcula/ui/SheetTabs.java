package com.calcula.ui;

import java.util.List;
import java.util.function.IntConsumer;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * The strip of open sheets.
 *
 * <p>Hand-built rather than a {@code TabPane}, because a TabPane owns its tabs' content and there is
 * only one set of views here: the stack and the trail are single controls that show whichever sheet is
 * current. Putting the same node in several tabs means reparenting it on every switch, which is a
 * fragile thing to build a document model on.
 *
 * <p><b>Hidden while there is one sheet.</b> A row of chrome that always says "Untitled" and nothing
 * else is chrome for its own sake — the single-sheet case is the overwhelmingly common one and looks
 * exactly as it did before tabs existed. The strip appears when there is a choice to make.
 */
public final class SheetTabs {

    /** What a tab shows: its name, and whether it has unsaved work. */
    public record Entry(String title, boolean dirty) {}

    private final HBox bar = new HBox();
    private final IntConsumer onSelect;
    private final IntConsumer onClose;
    private final Runnable onNew;

    public SheetTabs(IntConsumer onSelect, IntConsumer onClose, Runnable onNew) {
        this.onSelect = onSelect;
        this.onClose = onClose;
        this.onNew = onNew;
        bar.getStyleClass().add("sheet-tabs");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setVisible(false);
        bar.setManaged(false);
    }

    public Region node() {
        return bar;
    }

    /** Redraw for the open sheets. Cheap: a handful of labels, rebuilt only when the set changes. */
    public void render(List<Entry> entries, int selected) {
        bar.getChildren().clear();
        // Managed as well as visible: an invisible-but-managed bar still takes its height, which
        // would leave a stripe of empty chrome above the stack in the single-sheet case.
        boolean show = entries.size() > 1;
        bar.setVisible(show);
        bar.setManaged(show);
        if (!show) {
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            bar.getChildren().add(tab(entries.get(i), i, i == selected));
        }
        bar.getChildren().add(plus());
    }

    private Region tab(Entry entry, int index, boolean selected) {
        Label name = new Label((entry.dirty() ? "• " : "") + entry.title());
        name.getStyleClass().add("sheet-tab-name");

        Label close = new Label("×");
        close.getStyleClass().add("sheet-tab-close");
        close.setTooltip(new Tooltip("Close this sheet"));
        close.setOnMouseClicked(e -> {
            // Consumed, or the click also selects the tab it is closing — which then selects
            // whatever slid into that index, at random from the user's point of view.
            e.consume();
            onClose.accept(index);
        });

        HBox tab = new HBox(6, name, close);
        tab.setAlignment(Pos.CENTER_LEFT);
        tab.getStyleClass().add("sheet-tab");
        if (selected) {
            tab.getStyleClass().add("selected");
        }
        if (entry.dirty()) {
            tab.getStyleClass().add("dirty");
        }
        tab.setOnMouseClicked(e -> onSelect.accept(index));
        return tab;
    }

    private Region plus() {
        Label plus = new Label("+");
        plus.getStyleClass().addAll("sheet-tab", "sheet-tab-new");
        plus.setTooltip(new Tooltip("New sheet"));
        plus.setOnMouseClicked(e -> onNew.run());
        return plus;
    }

    /** Visible for tests: what the strip currently shows. */
    List<String> titles() {
        return bar.getChildren().stream()
                .filter(n -> n instanceof HBox)
                .map(n -> ((Label) ((HBox) n).getChildren().get(0)).getText())
                .toList();
    }

    boolean isShowing() {
        return bar.isVisible();
    }

    /** Visible for tests, and for a caller that wants to drive the strip without a mouse. */
    void select(int index) {
        onSelect.accept(index);
    }
}
