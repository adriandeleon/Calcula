package com.calcula.ui;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import com.calcula.AppInfo;
import com.calcula.cas.CasEngine;
import com.calcula.cas.CasEngineLoader;

/**
 * The main window: trail on the left, stack in the centre, mode line and echo area along the bottom.
 *
 * <p>The stack is displayed the way Emacs Calc displays it — entry {@code 1:} is the TOP of the stack
 * and is drawn at the BOTTOM of the list, nearest the input. The backing list is in the same order, so
 * the last element is the top and an entry's displayed number is {@code size - index}.
 *
 * <p>This is scaffolding: input goes straight to the CAS rather than through an RPN reader, and there is
 * no keymap engine yet. The structure is the point — the four regions, the async engine, and the fact
 * that nothing here knows what a CAS is beyond {@link CasEngine}.
 */
public final class CalcWindow {

    private static final int MAX_TRAIL = 500;

    private final BorderPane root = new BorderPane();
    private final ObservableList<String> stack = FXCollections.observableArrayList();
    private final ObservableList<String> trail = FXCollections.observableArrayList();
    private final ListView<String> stackView = new ListView<>(stack);
    private final ListView<String> trailView = new ListView<>(trail);
    private final Label modes = new Label("deg  prec 12  symb");
    private final Label engineStatus = new Label("CAS: loading…");
    private final Label prompt = new Label("›");
    private final TextField input = new TextField();

    /** Serial worker: a CAS call can take hundreds of ms and must never run on the FX thread. */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cas-eval");
        t.setDaemon(true);
        return t;
    });

    private volatile CasEngine engine = CasEngineLoader.unavailable("still loading");

    public CalcWindow() {
        buildStack();
        buildTrail();

        SplitPane split = new SplitPane(trailView, stackView);
        split.setDividerPositions(0.28);
        SplitPane.setResizableWithParent(trailView, Boolean.FALSE);

        root.setCenter(split);
        root.setBottom(new VBox(buildModeLine(), buildEchoArea()));
        root.getStyleClass().add("calc-root");

        input.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                onEnter();
                e.consume();
            } else if (e.getCode() == KeyCode.BACK_SPACE && input.getText().isEmpty()) {
                drop(); // Calc's DEL: with nothing being typed, Backspace pops the stack.
                e.consume();
            }
        });
    }

    public Region getRoot() {
        return root;
    }

    public void focusInput() {
        input.requestFocus();
    }

    /** Hand the window its engine once {@link CasEngineLoader} has one. Safe to call off the FX thread. */
    public void setEngine(CasEngine loaded) {
        this.engine = loaded;
        Platform.runLater(() -> {
            engineStatus.setText(
                    loaded.available() ? "CAS: " + loaded.id() + " " + loaded.version() : "CAS: unavailable");
            engineStatus.getStyleClass().removeAll("cas-ok", "cas-missing");
            engineStatus.getStyleClass().add(loaded.available() ? "cas-ok" : "cas-missing");
        });
    }

    public void dispose() {
        worker.shutdownNow();
        engine.close();
    }

    // ---------------------------------------------------------------- regions

    private void buildStack() {
        stackView.getStyleClass().add("stack-view");
        stackView.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label index = new Label((stack.size() - getIndex()) + ":");
                index.getStyleClass().add("stack-index");
                index.setMinWidth(38);
                index.setAlignment(Pos.CENTER_RIGHT);

                Label content = new Label(value);
                content.getStyleClass().add("stack-value");

                HBox row = new HBox(10, index, content);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
                setText(null);
            }
        });
        // Renumbering is a whole-list property: dropping entry 3 changes what every entry below is
        // called, so a targeted refresh would be wrong.
        stack.addListener((javafx.collections.ListChangeListener<String>) c -> {
            stackView.refresh();
            if (!stack.isEmpty()) {
                stackView.scrollTo(stack.size() - 1);
            }
        });
    }

    private void buildTrail() {
        trailView.getStyleClass().add("trail-view");
        trailView.setPlaceholder(new Label("trail"));
    }

    private Region buildModeLine() {
        modes.getStyleClass().add("mode-item");
        engineStatus.getStyleClass().add("mode-item");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(modes, spacer, engineStatus);
        bar.getStyleClass().add("mode-line");
        return bar;
    }

    private Region buildEchoArea() {
        prompt.getStyleClass().add("echo-prompt");
        input.getStyleClass().add("echo-input");
        HBox.setHgrow(input, Priority.ALWAYS);
        HBox bar = new HBox(8, prompt, input);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("echo-area");
        return bar;
    }

    // ---------------------------------------------------------------- actions

    private void onEnter() {
        String text = input.getText().trim();
        if (text.isEmpty()) {
            duplicateTop(); // Calc: bare Enter duplicates the top of the stack.
            return;
        }
        input.clear();
        CasEngine current = engine;
        prompt.setText("…");
        worker.execute(() -> {
            String result;
            boolean failed = false;
            try {
                result = current.eval(text);
            } catch (Exception e) {
                result = e.getMessage();
                failed = true;
            }
            String value = result;
            boolean error = failed;
            Platform.runLater(() -> {
                prompt.setText("›");
                appendTrail(text);
                if (error) {
                    appendTrail("  ! " + value);
                    input.setText(text); // give the input back so it can be corrected
                    input.positionCaret(text.length());
                } else {
                    appendTrail("  = " + value);
                    stack.add(value);
                }
            });
        });
    }

    private void duplicateTop() {
        if (!stack.isEmpty()) {
            stack.add(stack.get(stack.size() - 1));
        }
    }

    private void drop() {
        if (!stack.isEmpty()) {
            stack.remove(stack.size() - 1);
        }
    }

    private void appendTrail(String line) {
        trail.add(line);
        if (trail.size() > MAX_TRAIL) {
            trail.remove(0, trail.size() - MAX_TRAIL);
        }
        trailView.scrollTo(trail.size() - 1);
    }

    /** Visible for tests: the stack from bottom to top. */
    public List<String> stackContents() {
        return List.copyOf(stack);
    }

    /** Visible for tests. */
    public List<String> trailContents() {
        return List.copyOf(trail);
    }

    /** Visible for tests: drive the echo area without a robot. */
    public void submit(String text) {
        input.setText(text);
        onEnter();
    }

    public String title() {
        return AppInfo.NAME + (AppInfo.isSnapshot() ? "  —  " + AppInfo.VERSION : "");
    }
}
