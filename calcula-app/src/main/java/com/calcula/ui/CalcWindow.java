package com.calcula.ui;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import com.calcula.AppInfo;
import com.calcula.cas.CasEngine;
import com.calcula.cas.CasEngineLoader;
import com.calcula.cas.CasException;
import com.calcula.command.CommandRegistry;
import com.calcula.expr.Expr;
import com.calcula.expr.Exprs;
import com.calcula.input.AlgebraicReader;
import com.calcula.input.Reader;
import com.calcula.input.RpnReader;
import com.calcula.key.KeyDispatcher;
import com.calcula.key.Keymap;
import com.calcula.machine.CalcState;
import com.calcula.machine.Evaluator;
import com.calcula.machine.Machine;
import com.calcula.machine.MachineException;
import com.calcula.machine.Op;
import com.calcula.machine.TrailEntry;
import com.calcula.parse.Formatter;
import com.calcula.plot.ExprCompiler;
import com.calcula.plot.GraphicsScene;
import com.calcula.plot.PlotAnalysis;
import com.calcula.plot.PlotException;
import com.calcula.plot.PlotValue;
import com.calcula.ui.math.MathLayout;
import com.calcula.ui.math.MathStyle;
import com.calcula.ui.plot.PlotCanvas;

/**
 * The main window: trail on the left, stack in the centre, mode line and echo area along the bottom.
 *
 * <p>The stack is displayed the way Emacs Calc displays it — entry {@code 1:} is the TOP and is drawn at
 * the BOTTOM, nearest the input.
 *
 * <h2>Threading</h2>
 *
 * <p>{@link Machine} is not thread-safe and evaluation can reach the CAS, which takes hundreds of
 * milliseconds. So <b>every</b> machine call happens on one worker thread, and the window renders from
 * an immutable {@link CalcState} snapshot published back to the FX thread. Reading the machine from the
 * FX thread "just to draw" is the obvious shortcut and is exactly the data race.
 *
 * <h2>Keys</h2>
 *
 * <p>Chords go through {@link KeyDispatcher}; anything {@link Chords} declines to translate is ordinary
 * typing and reaches the text field. Which input model the typed line is then read with —
 * {@link RpnReader} or {@link AlgebraicReader} — is switchable at runtime and deliberately not yet
 * decided; algebraic is the provisional start because it is the gentler of the two to meet first.
 */
public final class CalcWindow {

    /** Point size for rendered stack entries. */
    private static final double STACK_MATH_SIZE = 17;

    /** A plot on the stack is a thumbnail; it is still fully interactive. */
    private static final double STACK_PLOT_WIDTH = 360;

    private static final double STACK_PLOT_HEIGHT = 200;

    /**
     * Space between the stack's gutter rail and the entry number.
     *
     * <p>The cell's own left padding is zero so the rail can sit flush against the edge, so this is
     * what stands in for it — 3 px of rail plus this gap lands the number where the old 12 px
     * padding used to put it.
     */
    private static final double GUTTER_GAP = 8;

    private final BorderPane root = new BorderPane();
    private final ObservableList<Expr> stack = FXCollections.observableArrayList();
    private final ObservableList<TrailEntry> trailLines = FXCollections.observableArrayList();
    private final ListView<Expr> stackView = new ListView<>(stack);
    private final ListView<TrailEntry> trailView = new ListView<>(trailLines);
    private final Label modes = new Label();
    private final Label engineStatus = new Label("CAS: loading…");
    private final Label prompt = new Label("›");
    private final TextField input = new TextField();

    private final CommandRegistry registry = new CommandRegistry();
    private final Keymap keymap = new Keymap();
    private final KeyDispatcher dispatcher = new KeyDispatcher(keymap, registry);
    private final Machine machine;

    /** Serial worker: the machine is single-threaded by contract, and a CAS call is slow. */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "calc-machine");
        t.setDaemon(true);
        return t;
    });

    /**
     * Analyses, keyed by the plot they belong to.
     *
     * <p>Computed once when the plot is made, on the worker thread where the engine lives, and read
     * later on the FX thread when the cell is built. Keyed by value, so undo and redo find the same
     * analysis rather than recomputing it.
     */
    private final java.util.Map<Expr, PlotAnalysis> plotAnalyses = new java.util.concurrent.ConcurrentHashMap<>();

    private volatile CasEngine engine = CasEngineLoader.unavailable("still loading");
    private volatile Reader reader = new AlgebraicReader();

    public CalcWindow() {
        machine = new Machine(Evaluator.numericThen(this::askEngine));

        buildStack();
        buildTrail();
        registerCommands();
        installDefaultKeymap();

        SplitPane split = new SplitPane(trailView, stackView);
        split.setDividerPositions(0.28);
        SplitPane.setResizableWithParent(trailView, Boolean.FALSE);

        root.setCenter(split);
        root.setBottom(new VBox(buildModeLine(), buildEchoArea()));
        root.getStyleClass().add("calc-root");

        input.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        refreshModeLine();
    }

    public Region getRoot() {
        return root;
    }

    public void focusInput() {
        input.requestFocus();
    }

    public String title() {
        return AppInfo.NAME + (AppInfo.isSnapshot() ? "  —  " + AppInfo.VERSION : "");
    }

    // ---------------------------------------------------------------- engine

    /** Hand the window its engine once {@link CasEngineLoader} has one. Safe to call off the FX thread. */
    public void setEngine(CasEngine loaded) {
        this.engine = loaded;
        Platform.runLater(() -> {
            engineStatus.setText(
                    loaded.available() ? "CAS: " + loaded.id() + " " + loaded.version() : "CAS: unavailable");
            engineStatus.getStyleClass().removeAll("cas-ok", "cas-missing");
            engineStatus.getStyleClass().add(loaded.available() ? "cas-ok" : "cas-missing");
            // "unavailable" on its own is a symptom, not a diagnosis. Put the reason where someone
            // looking at the words "CAS: unavailable" will actually find it.
            engineStatus.setTooltip(loaded.diagnostic().isBlank() ? null : new Tooltip(loaded.diagnostic()));
        });
        if (!loaded.available() && !loaded.diagnostic().isBlank()) {
            onMachine(m -> m.record(new TrailEntry(TrailEntry.Kind.NOTE, "no CAS: " + loaded.diagnostic())));
        }
    }

    /**
     * The engine half of the evaluator.
     *
     * <p>With no engine loaded the expression comes back untouched rather than raising. That is the
     * degradation that matters: exact arithmetic still works through the numeric fold, and {@code x + 1}
     * simply stays {@code x + 1} instead of every symbolic entry becoming an error.
     */
    private Expr askEngine(Expr e) {
        if (PlotValue.isPlot(e)) {
            // $Plot is ours, not the engine's. Sending it would be a pointless round trip at best and
            // an unrecognised-symbol error at worst.
            return e;
        }
        CasEngine current = engine;
        if (!current.available()) {
            return e;
        }
        try {
            return current.eval(e);
        } catch (CasException ex) {
            throw new MachineException(ex.getMessage(), ex);
        }
    }

    public void dispose() {
        worker.shutdownNow();
        engine.close();
    }

    // ---------------------------------------------------------------- commands

    private void registerCommands() {
        registry.register("input.submit", "Enter", "Read the typed line onto the stack", this::submitInput);
        registry.register("stack.drop", "Drop", "Remove the top value", () -> machineOp(new Op.Drop(1)));
        registry.register("stack.dup", "Duplicate", "Copy the top value", () -> machineOp(new Op.Dup(1)));
        registry.register("stack.swap", "Swap", "Exchange the top two values", () -> machineOp(new Op.Swap()));
        registry.register("stack.roll", "Roll", "Rotate the top three values", () -> machineOp(new Op.Roll(3)));
        registry.register("stack.clear", "Clear", "Empty the stack", () -> machineOp(new Op.Clear()));
        registry.register(
                "stack.evaluate", "Evaluate", "Re-evaluate the top value", () -> machineOp(new Op.Evaluate()));
        registry.register(
                "edit.undo",
                "Undo",
                "Step back one operation",
                () -> onMachine(m -> {
                    if (!m.undo()) {
                        m.record(new TrailEntry(TrailEntry.Kind.NOTE, "nothing to undo"));
                    }
                }));
        registry.register(
                "edit.redo",
                "Redo",
                "Step forward again",
                () -> onMachine(m -> {
                    if (!m.redo()) {
                        m.record(new TrailEntry(TrailEntry.Kind.NOTE, "nothing to redo"));
                    }
                }));
        registry.register(
                "plot.function",
                "Plot",
                "Draw the top of the stack as a curve",
                () -> onMachine(m -> {
                    Expr top = m.state().at(1);
                    if (PlotValue.isPlot(top)) {
                        m.record(new TrailEntry(TrailEntry.Kind.NOTE, "that is already a plot"));
                        return;
                    }
                    // The formula is NOT consumed: Calc graphs without taking the value away, and having the
                    // expression still there is the point of plotting it.
                    Expr plot = PlotValue.of(top, PlotValue.inferVariable(top), -10, 10);
                    // On the worker thread, where the engine is reachable and a slow Solve cannot stall the UI.
                    plotAnalyses.put(plot, analyse(plot));
                    m.apply(new Op.Push(plot));
                }));
        registry.register("input.toggleModel", "Toggle entry model", "Switch between algebraic and RPN entry", () -> {
            reader = reader instanceof AlgebraicReader ? new RpnReader() : new AlgebraicReader();
            onMachine(m -> m.record(new TrailEntry(TrailEntry.Kind.NOTE, "entry: " + reader.id())));
        });
    }

    /** Provisional bindings. Only chords appear here; plain letters have to keep typing. */
    private void installDefaultKeymap() {
        keymap.bind("RET", "input.submit");
        keymap.bind("TAB", "stack.swap");
        keymap.bind("M-TAB", "stack.roll");
        keymap.bind("DEL", "stack.drop");
        keymap.bind("C-z", "edit.undo");
        keymap.bind("C-S-z", "edit.redo");
        keymap.bind("C-x u", "edit.undo");
        keymap.bind("C-x k", "stack.clear");
        keymap.bind("C-x e", "stack.evaluate");
        keymap.bind("C-x d", "stack.dup");
        keymap.bind("M-i", "input.toggleModel");
        keymap.bind("M-p", "plot.function");
        // Both spellings: Chords emits Cmd- on macOS and C- elsewhere.
        keymap.bind("C-c", "edit.copy");
        keymap.bind("Cmd-c", "edit.copy");
    }

    private void onKey(KeyEvent event) {
        String chord = Chords.chordFor(event);
        if (chord == null) {
            return; // ordinary typing
        }
        // Backspace must delete text when there is text to delete; only an empty field pops the stack.
        // This is the one place the widget's own job overrides a binding.
        if ("DEL".equals(chord) && !input.getText().isEmpty()) {
            return;
        }
        KeyDispatcher.Result result = dispatcher.press(chord);
        if (result.consumed()) {
            event.consume();
        }
        if (result.outcome() == KeyDispatcher.Outcome.PENDING) {
            setPrompt(result.sequence() + "-", true);
        } else if (result.outcome() != KeyDispatcher.Outcome.RAN) {
            setPrompt("›", false);
        }
    }

    /**
     * A stack entry that is a plot, drawn rather than typeset.
     *
     * <p>Compiled here rather than at plot time so a resize or a theme change redraws from the same
     * expression, and so a formula that cannot be evaluated numerically says why in the panel instead
     * of failing the push.
     */
    private Region plotFor(Expr plot) {
        PlotCanvas canvas = new PlotCanvas(STACK_PLOT_WIDTH, STACK_PLOT_HEIGHT);
        try {
            canvas.show(
                    ExprCompiler.compile(PlotValue.body(plot), PlotValue.variable(plot)),
                    PlotValue.xMin(plot),
                    PlotValue.xMax(plot));
            canvas.setAnalysis(plotAnalyses.getOrDefault(plot, PlotAnalysis.NONE));
        } catch (PlotException e) {
            canvas.showMessage(e.getMessage());
        }
        return canvas;
    }

    /**
     * A picture the engine produced, e.g. from typing {@code Plot(sin(x), [x, 0, 6])}.
     *
     * <p>No new encoding: the engine already returns a {@code Graphics} value, so it lands on the stack
     * like any other result and is simply drawn rather than typeset.
     */
    private Region sceneFor(Expr graphics) {
        PlotCanvas canvas = new PlotCanvas(STACK_PLOT_WIDTH, STACK_PLOT_HEIGHT);
        try {
            canvas.showScene(GraphicsScene.parse(graphics));
        } catch (PlotException e) {
            canvas.showMessage(e.getMessage());
        }
        return canvas;
    }

    /**
     * Ask the algebra where the poles and turning points are.
     *
     * <p>Best effort throughout: annotations make a plot better and are never a reason to fail one, so
     * every failure here returns nothing to draw rather than propagating.
     */
    private PlotAnalysis analyse(Expr plot) {
        CasEngine cas = engine;
        if (!cas.available()) {
            return PlotAnalysis.NONE;
        }
        Expr body = PlotValue.body(plot);
        String variable = PlotValue.variable(plot);
        try {
            List<Expr> poles = PlotAnalysis.roots(cas.eval(PlotAnalysis.asymptoteQuery(body, variable)));
            List<Expr> turning = PlotAnalysis.roots(cas.eval(PlotAnalysis.criticalQuery(body, variable)));
            return PlotAnalysis.of(
                    poles, turning, ExprCompiler.compile(body, variable), PlotValue.xMin(plot), PlotValue.xMax(plot));
        } catch (CasException | RuntimeException e) {
            // RuntimeException already covers PlotException, which extends it.
            return PlotAnalysis.NONE;
        }
    }

    // ---------------------------------------------------------------- machine access

    /**
     * Run something against the machine on the worker thread and republish the result.
     *
     * <p>Every machine call goes through here. What comes back to the FX thread is an immutable
     * snapshot, so the window never touches the machine from two threads.
     */
    private void onMachine(Consumer<Machine> work) {
        worker.execute(() -> {
            try {
                work.accept(machine);
            } catch (RuntimeException e) {
                machine.recordError(describe(e));
            }
            CalcState snapshot = machine.state();
            // Copied here, on the worker, so the FX thread never reads the machine.
            List<TrailEntry> trail = List.copyOf(machine.trail());
            Platform.runLater(() -> publish(snapshot, trail));
        });
    }

    /**
     * The prompt is the status indicator.
     *
     * <p>With no toolbar and no dialogs, the one glyph the eye is already resting on carries the
     * machine's state: ready, working, prefix held, failed. Text and style move together here so
     * the two cannot drift — a pending sequence left showing in the ready colour reads as an
     * ordinary prompt with junk in it.
     */
    private void setPrompt(String text, boolean pending) {
        prompt.setText(text);
        prompt.getStyleClass().remove("pending");
        if (pending) {
            prompt.getStyleClass().add("pending");
        }
    }

    private void machineOp(Op op) {
        onMachine(m -> m.apply(op));
    }

    private void publish(CalcState snapshot, List<TrailEntry> trail) {
        stack.setAll(snapshot.stack());
        trailLines.setAll(trail);
        if (!trailLines.isEmpty()) {
            trailView.scrollTo(trailLines.size() - 1);
        }
        refreshModeLine();
        setPrompt("›", false);
    }

    /**
     * One trail line as it is displayed.
     *
     * <p>The sigil is added here rather than at the point of recording, so {@link TrailEntry} keeps
     * carrying its kind as data — which is what lets {@link #trailCellClass} colour it.
     */
    private static String renderTrail(TrailEntry entry) {
        return switch (entry.kind()) {
            case INPUT -> entry.text();
            case RESULT -> "  = " + entry.text();
            case ERROR -> "  ! " + entry.text();
            case NOTE -> "  · " + entry.text();
        };
    }

    /** The state-language class for a trail line. See the trail rules in {@code app.css}. */
    private static String trailCellClass(TrailEntry entry) {
        return switch (entry.kind()) {
            case INPUT -> "trail-input";
            case RESULT -> "trail-result";
            case ERROR -> "trail-error";
            case NOTE -> "trail-note";
        };
    }

    private static String describe(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.toString() : message;
    }

    // ---------------------------------------------------------------- input

    private void submitInput() {
        String text = input.getText().trim();
        if (text.isEmpty()) {
            machineOp(new Op.Dup(1)); // Calc: bare Enter duplicates the top of the stack
            return;
        }
        input.clear();
        setPrompt("…", false);
        Reader current = reader;
        onMachine(m -> {
            m.recordInput(text);
            try {
                m.applyAll(current.read(text, m.state()));
                if (!m.state().isEmpty()) {
                    m.recordResult(m.state().at(1));
                }
            } catch (RuntimeException e) {
                m.recordError(describe(e));
                // Hand the line back so it can be corrected rather than retyped.
                Platform.runLater(() -> {
                    input.setText(text);
                    input.positionCaret(text.length());
                });
            }
        });
    }

    // ---------------------------------------------------------------- regions

    private void buildStack() {
        stackView.getStyleClass().add("stack-view");
        stackView.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Expr value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                // A CAS spends its life moving between exact and approximate. Modes reports the
                // POLICY (symbolic, fractions); nothing reported the VALUE. One Flt anywhere in
                // the tree contaminates it, so this is containsInexact and not !isExact — the
                // latter is shallow and would mark every symbolic result approximate.
                boolean inexact = Exprs.containsInexact(value);

                // Always present, usually transparent: a value acquiring a marker must not shift
                // the text beside it.
                Region gutter = new Region();
                gutter.getStyleClass().add("stack-gutter");

                Label index = new Label((stack.size() - getIndex()) + ":");
                index.getStyleClass().add("stack-index");
                index.setMinWidth(38);
                index.setAlignment(Pos.CENTER_RIGHT);

                if (inexact) {
                    gutter.getStyleClass().add("inexact");
                    index.getStyleClass().add("inexact");
                }

                Region content = PlotValue.isPlot(value)
                        ? plotFor(value)
                        : GraphicsScene.isGraphics(value)
                                ? sceneFor(value)
                                : MathLayout.render(value, MathStyle.of(STACK_MATH_SIZE));
                content.getStyleClass().add("stack-value");

                // Two boxes, because the rail and the formula want opposite alignments and one box
                // cannot give both. Inside: baseline, so the entry number sits on the formula's own
                // baseline — which for a fraction is nowhere near its middle. Outside: fill height,
                // so the rail spans the row however tall the formula turns out to be.
                //
                // A rail in the BASELINE_LEFT box would vanish: a plain Region reports
                // BASELINE_OFFSET_SAME_AS_HEIGHT, so it gets aligned by its own box and, with no
                // content to give it height, draws nothing.
                HBox formula = new HBox(10, index, content);
                formula.setAlignment(Pos.BASELINE_LEFT);
                HBox.setHgrow(formula, Priority.ALWAYS);

                HBox row = new HBox(GUTTER_GAP, gutter, formula);
                row.setFillHeight(true);
                HBox.setHgrow(gutter, Priority.NEVER); // a fixed rail, not a flexible column
                setGraphic(row);
                setText(null);
            }
        });
        // Renumbering is a whole-list property: dropping entry 3 changes what every entry below is
        // called, so a targeted refresh would be wrong.
        stack.addListener((javafx.collections.ListChangeListener<Expr>) c -> {
            stackView.refresh();
            if (!stack.isEmpty()) {
                stackView.scrollTo(stack.size() - 1);
            }
        });
    }

    private void buildTrail() {
        trailView.getStyleClass().add("trail-view");
        trailView.setPlaceholder(new Label("trail"));
        trailView.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(TrailEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                // A recycled cell keeps the class from whatever it showed last, so every kind has
                // to be cleared before the current one is added — otherwise scrolling smears the
                // colours down the list.
                getStyleClass().removeAll("trail-input", "trail-result", "trail-error", "trail-note");
                if (empty || entry == null) {
                    setText(null);
                    return;
                }
                setText(renderTrail(entry));
                getStyleClass().add(trailCellClass(entry));
            }
        });
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

    private void refreshModeLine() {
        modes.setText(machine.modes().describe() + "  " + reader.label());
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

    // ---------------------------------------------------------------- test seams

    /** Visible for tests: the stack from bottom to top. */
    public List<Expr> stackContents() {
        return List.copyOf(stack);
    }

    /** Visible for tests: the stack as it is displayed. */
    public List<String> stackDisplay() {
        return stack.stream().map(Formatter::format).toList();
    }

    /** Visible for tests: the trail as it is displayed, sigils included. */
    public List<String> trailContents() {
        return trailLines.stream().map(CalcWindow::renderTrail).toList();
    }

    /** Visible for tests: drive the echo area without a robot. */
    public void submit(String text) {
        input.setText(text);
        submitInput();
    }

    /** Visible for tests: run a command by id, as a key binding would. */
    public boolean run(String commandId) {
        return registry.run(commandId);
    }

    /** Visible for tests: feed a chord, as the key filter would. */
    public KeyDispatcher.Result press(String chord) {
        return dispatcher.press(chord);
    }

    public String readerId() {
        return reader.id();
    }

    public CommandRegistry commands() {
        return registry;
    }
}
